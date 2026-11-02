# Data contract - Android baseline (R0)

Author: Beam (provisional draft; GLM canonical pending), baseline, the web `types.ts`/src/api are the reference contract.

- Direct public sources only (no API keys): during bench, we verified the API routes are
  public and provide real data outputs.
- Mobile must use direct endpoint calls without web deployment required.
- Caching cadence:
  - foreground 5 min
  - background 15-30 min
  - manual refresh on demand
- Staleneness always visible to the user.
- Endpoint failover/fallback pattern:
  - ionosondes: GIRO first, then kc2g.
  - capture failures: degraded, not invented data.
- Ebro FTP: deferred slice; fixtures SAO never bundled (permission pending).

Reference APIs from web README:
- /api/{ae,aurora,cme,dst,flares,ionosondes,lightning,msis,rbe,satellites,solar-image,space-weather,voyager,wSpr}

Signature guard: keep `package.json` public (no secret).


