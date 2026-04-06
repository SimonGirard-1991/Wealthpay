FROM golang:1.26.1-alpine AS build
WORKDIR /app
COPY go.mod .
COPY main.go .
RUN CGO_ENABLED=0 go build -o alertmanager-discord main.go

FROM alpine:3.21
COPY --from=build /app/alertmanager-discord /usr/local/bin/
ENTRYPOINT ["alertmanager-discord"]
