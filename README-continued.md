# NearBuy — continued UI refinements

This source package builds on the marketplace workflow version.

## Run locally

```powershell
docker compose -p nearbuy-continued up -d --build
```

The app is mapped to http://localhost:8087. This package uses its own Compose project name and database volume. Keep existing NearBuy containers and volumes intact; do not use `docker compose down -v` on other projects.

## Frontend updates in this pass
- Authentication modal is scrollable within the viewport and its fields/buttons remain reachable on short screens.
- Optional browser geolocation updates the coordinates used for nearby-offer searches. If permission is declined, the default Guntur area remains usable.
- Product cards have rounded corners, subtle elevation and restrained hover motion.
- Product quick view animates into place and compares same-model offers returned by the current search.
- Reduced-motion preferences are respected.

Location uses the browser Geolocation API and requires permission; production deployment should use HTTPS. Road-route distance is not calculated—distance is based on the backend's existing offer endpoint.

## Retailer address verification update

This update adds the additive Flyway migration `V5__shop_address_change_requests.sql`. Existing migration files are unchanged. Retailers use the address saved on their shop profile for all listings; the listing form no longer accepts a second address. Unverified retailers see a clear “Not yet verified” state and listing endpoints return an explanatory `403` response. A verified retailer can submit an address-change request, which is stored separately and reviewed in the admin console. Until approval, the currently verified address remains the shop's public location and new listing/edits are paused. Approval applies the proposed address and coordinates; rejection leaves the existing verified address unchanged. Both sides receive persistent notifications.

The homepage hero art panel now uses the supplied community-hands photo. Product detail entry has a restrained 3D rotation/scale transition, with reduced-motion support retained.

Run this extracted version with a unique Compose project name and the configured port 8087:

```powershell
docker compose -p nearbuy-continued up -d --build
Invoke-RestMethod http://localhost:8087/api/health
```

The new Compose project gets its own database volume. Do not use `docker compose down -v` on any of the existing NearBuy projects. Build and automated-test results still need to be verified on the user's machine; the frontend dependency installation was incomplete in the build environment.


Latest UI fix: hero hands are rendered as an explicit image element and the product details now use a pronounced 3D flip-in/reverse flip-out animation. Verify locally with Docker before deployment.
