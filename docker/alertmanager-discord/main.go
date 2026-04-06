package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"
)

type Alert struct {
	Status      string            `json:"status"`
	Labels      map[string]string `json:"labels"`
	Annotations map[string]string `json:"annotations"`
}

type AlertManagerPayload struct {
	Status      string            `json:"status"`
	Alerts      []Alert           `json:"alerts"`
	GroupLabels map[string]string `json:"groupLabels"`
}

type DiscordEmbed struct {
	Title       string `json:"title"`
	Description string `json:"description"`
	Color       int    `json:"color"`
}

type DiscordPayload struct {
	Content string         `json:"content,omitempty"`
	Embeds  []DiscordEmbed `json:"embeds"`
}

type discordRateLimitBody struct {
	Message    string  `json:"message"`
	RetryAfter float64 `json:"retry_after"`
	Global     bool    `json:"global"`
}

const (
	defaultPort                    = "9096"
	maxRequestBodyBytes      int64 = 1 << 20
	maxDiscordEmbeds               = 10
	discordTitleMaxRunes           = 256
	discordDescMaxRunes            = 4096
	maxDiscord429Retries           = 3
	discordRequestTimeout          = 10 * time.Second
	defaultDiscordRetryDelay       = 2 * time.Second
	maxDiscordRetryDelay           = 10 * time.Second
	serverWriteTimeout             = 45 * time.Second
)

type app struct {
	webhookURL         string
	criticalWebhookURL string
	client             *http.Client
}

func (a *app) handler(w http.ResponseWriter, r *http.Request) {
	a.handleAlerts(w, r, a.webhookURL, "")
}

func (a *app) criticalHandler(w http.ResponseWriter, r *http.Request) {
	a.handleAlerts(w, r, a.criticalWebhookURL, "@here")
}

func (a *app) handleAlerts(w http.ResponseWriter, r *http.Request, webhookURL string, mention string) {
	if r.Method != http.MethodPost {
		w.Header().Set("Allow", http.MethodPost)
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, maxRequestBodyBytes)
	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {
			log.Printf("ERROR: failed to close request body: %v", err)
		}
	}(r.Body)

	var payload AlertManagerPayload
	if err := json.NewDecoder(r.Body).Decode(&payload); err != nil {
		http.Error(w, "failed to parse JSON", http.StatusBadRequest)
		return
	}

	if len(payload.Alerts) == 0 {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("no alerts to forward"))
		return
	}

	embeds := make([]DiscordEmbed, 0, len(payload.Alerts))
	for _, alert := range payload.Alerts {
		color := 0xFF0000
		status := "FIRING"
		if strings.EqualFold(alert.Status, "resolved") {
			color = 0x00FF00
			status = "RESOLVED"
		}

		severity := strings.ToUpper(strings.TrimSpace(alert.Labels["severity"]))
		if severity == "" {
			severity = "UNKNOWN"
		}

		alertName := strings.TrimSpace(alert.Labels["alertname"])
		if alertName == "" {
			alertName = "Unnamed alert"
		}
		title := truncateRunes(fmt.Sprintf("[%s] %s - %s", status, severity, alertName), discordTitleMaxRunes)

		description := firstNonEmpty(
			alert.Annotations["description"],
			alert.Annotations["summary"],
		)
		if description == "" {
			description = "No description provided."
		}
		description = truncateRunes(description, discordDescMaxRunes)

		embeds = append(embeds, DiscordEmbed{
			Title:       title,
			Description: description,
			Color:       color,
		})
	}

	if strings.EqualFold(payload.Status, "resolved") {
		mention = ""
	}

	if err := a.sendDiscordMessages(r.Context(), webhookURL, embeds, mention); err != nil {
		log.Printf("ERROR: failed to send to Discord: %v", err)
		http.Error(w, "failed to send to Discord", http.StatusBadGateway)
		return
	}

	log.Printf("OK: sent %d alert(s) to Discord [%s]", len(payload.Alerts), payload.Status)
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte("ok"))
}

func (a *app) sendDiscordMessages(ctx context.Context, webhookURL string, embeds []DiscordEmbed, mention string) error {
	for i := 0; i < len(embeds); i += maxDiscordEmbeds {
		end := i + maxDiscordEmbeds
		if end > len(embeds) {
			end = len(embeds)
		}
		content := ""
		if i == 0 {
			content = mention
		}
		if err := a.sendDiscordPayload(ctx, webhookURL, DiscordPayload{Content: content, Embeds: embeds[i:end]}); err != nil {
			return err
		}
	}
	return nil
}

func (a *app) sendDiscordPayload(ctx context.Context, webhookURL string, payload DiscordPayload) error {
	jsonData, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("marshal payload: %w", err)
	}

	var lastErr error
	for attempt := 0; attempt <= maxDiscord429Retries; attempt++ {
		retryAfter, retryable, err := a.postDiscordOnce(ctx, webhookURL, jsonData)
		if err == nil {
			return nil
		}
		lastErr = err

		if !retryable || attempt == maxDiscord429Retries {
			return lastErr
		}

		if retryAfter <= 0 {
			retryAfter = defaultDiscordRetryDelay
		}
		if retryAfter > maxDiscordRetryDelay {
			return fmt.Errorf("discord retry_after %s exceeds local retry cap %s: %w", retryAfter, maxDiscordRetryDelay, err)
		}

		log.Printf("WARN: Discord rate limited, retrying in %s (%d/%d)", retryAfter, attempt+1, maxDiscord429Retries)
		timer := time.NewTimer(retryAfter)
		select {
		case <-ctx.Done():
			if !timer.Stop() {
				<-timer.C
			}
			return fmt.Errorf("request canceled while waiting to retry Discord webhook: %w", ctx.Err())
		case <-timer.C:
		}
	}

	return lastErr
}

func (a *app) postDiscordOnce(ctx context.Context, webhookURL string, jsonData []byte) (time.Duration, bool, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, webhookURL, bytes.NewReader(jsonData))
	if err != nil {
		return 0, false, fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := a.client.Do(req)
	if err != nil {
		return 0, false, fmt.Errorf("perform request: %w", err)
	}
	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {
			log.Printf("ERROR: failed to close response body: %v", err)
		}
	}(resp.Body)

	respBody, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
	if resp.StatusCode >= 200 && resp.StatusCode < 300 {
		return 0, false, nil
	}
	if resp.StatusCode == http.StatusTooManyRequests {
		retryAfter := parseDiscordRetryAfter(resp.Header, respBody)
		return retryAfter, true, fmt.Errorf("Discord returned 429: %s", strings.TrimSpace(string(respBody)))
	}

	return 0, false, fmt.Errorf("Discord returned %d: %s", resp.StatusCode, strings.TrimSpace(string(respBody)))
}

func parseDiscordRetryAfter(headers http.Header, body []byte) time.Duration {
	if retryAfter, ok := parseDurationSeconds(headers.Get("Retry-After")); ok {
		return retryAfter
	}
	if retryAfter, ok := parseDurationSeconds(headers.Get("X-RateLimit-Reset-After")); ok {
		return retryAfter
	}

	var rateLimitBody discordRateLimitBody
	if err := json.Unmarshal(body, &rateLimitBody); err == nil && rateLimitBody.RetryAfter > 0 {
		return durationFromDiscordRetryAfter(rateLimitBody.RetryAfter)
	}

	return defaultDiscordRetryDelay
}

func durationFromDiscordRetryAfter(value float64) time.Duration {
	return time.Duration(value * float64(time.Second))
}

func parseDurationSeconds(value string) (time.Duration, bool) {
	seconds, err := strconv.ParseFloat(strings.TrimSpace(value), 64)
	if err != nil || seconds <= 0 {
		return 0, false
	}
	return time.Duration(seconds * float64(time.Second)), true
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if trimmed := strings.TrimSpace(v); trimmed != "" {
			return trimmed
		}
	}
	return ""
}

func truncateRunes(s string, maxLen int) string {
	if maxLen <= 0 {
		return ""
	}
	r := []rune(s)
	if len(r) <= maxLen {
		return s
	}
	if maxLen <= 3 {
		return string(r[:maxLen])
	}
	return string(r[:maxLen-3]) + "..."
}

func main() {
	webhookURL := strings.TrimSpace(os.Getenv("DISCORD_WEBHOOK_URL"))
	if webhookURL == "" {
		log.Fatal("DISCORD_WEBHOOK_URL not set")
	}

	criticalWebhookURL := strings.TrimSpace(os.Getenv("DISCORD_WEBHOOK_CRITICAL_URL"))
	if criticalWebhookURL == "" {
		criticalWebhookURL = webhookURL
	}

	app := &app{
		webhookURL:         webhookURL,
		criticalWebhookURL: criticalWebhookURL,
		client: &http.Client{
			Timeout: discordRequestTimeout,
		},
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	mux.HandleFunc("/", app.handler)
	mux.HandleFunc("/critical", app.criticalHandler)

	port := os.Getenv("PORT")
	if strings.TrimSpace(port) == "" {
		port = defaultPort
	}

	server := &http.Server{
		Addr:              ":" + port,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      serverWriteTimeout,
		IdleTimeout:       60 * time.Second,
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	go func() {
		log.Printf("Listening on :%s", port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server error: %v", err)
		}
	}()

	<-ctx.Done()
	log.Println("Shutting down...")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := server.Shutdown(shutdownCtx); err != nil {
		log.Fatalf("Shutdown failed: %v", err)
	}
	log.Println("Shutdown complete")
}
