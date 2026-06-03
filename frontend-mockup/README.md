# WealthPay — Maquette front-end

Maquette d'une console back-office cohérente pour WealthPay, anticipant
l'arrivée du bounded context **`customer`**.

> Maquette statique uniquement (HTML/CSS/JS autonome, zéro build).
> Ouvrir `index.html` dans un navigateur.

## Direction artistique

« Banque privée suisse × terminal fintech » — encre profonde, ivoire,
accent laiton. Typographie : **Fraunces** (titres serif), **Hanken Grotesk**
(corps), **JetBrains Mono** (chiffres tabulaires, identifiants, montants).

Pas d'esthétique « AI slop » : pas d'Inter/Roboto, pas de dégradé violet
sur fond blanc. Chiffres alignés (tabular-nums) comme attendu d'un produit
financier.

## Ancrage sur le domaine réel

Les écrans reprennent fidèlement le modèle de l'API OpenAPI et du domaine
event-sourcé :

| Concept domaine | Dans la maquette |
|---|---|
| `AccountResponse` (`balanceAmount`, `reservedAmount`, `status`) | Bandeau Solde / Réservé / Disponible |
| `AccountStatus` `OPENED` / `CLOSED` | Pastilles de statut |
| `SupportedCurrency` (USD, EUR, CHF, GBP…) | Comptes multi-devises |
| `deposits` / `withdrawals` / `reservations` + `Transaction-Id` | Modale d'action (idempotence) |
| `ReservationResult` `RESERVED`/`CAPTURED`/`CANCELED`/`NO_EFFECT` | Tableau des réservations |
| Événements `AccountOpened`, `FundsCredited`, `FundsDebited`, `FundsReserved`, `ReservationCaptured`, `ReservationCanceled`, `AccountClosed` | Journal d'événements + flux live |
| Snapshots / versions / projection lag | Métadonnées du journal et carte d'état plateforme |

## Le futur BC `customer` (anticipé)

Écrans **Clients** (liste) et **Fiche client 360** préfigurant le module
`customer` (CLOSED, dépendant de `shared`, comme `account`) :

- Identité, segment (Retail / Premier / Private Wealth), statut KYC, note de risque.
- Le client est l'agrégat racine qui **possède** des comptes.
- Relation **par référence** (`customerId`), sans couplage de modèle entre
  les deux BCs — orchestrable par événements
  (`CustomerOnboarded` → `AccountOpened`).
- Vue consolidée multi-devises de l'encours (AUM).

## Écrans

1. **Vue d'ensemble** — KPIs, derniers clients, flux d'événements live.
2. **Clients** — portefeuille (nouveau BC).
3. **Fiche client 360** — profil, conformité KYC, comptes détenus, activité.
4. **Détail compte** — solde/réservé/disponible, actions, réservations, journal d'événements.

Navigation entre écrans via la barre latérale et les liens contextuels.
