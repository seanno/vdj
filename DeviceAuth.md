# Device Code Authentication for Agate

## Background

The Agate tenant (Adaptive Biotechnologies) requires MFA, which makes the existing
`UserPass` auth flow (`UsernamePasswordCredential`) unusable from external environments.
The `OnBehalfOf` flow still works when VDJ is deployed inside the Agate Azure tenant
with EasyAuth configured, but not from standalone/desktop deployments.

This document describes the `DeviceCode` auth type added to address this.

## How It Works

The implementation uses the OAuth 2.0 Device Authorization Grant (RFC 8628) in a
**stateless** fashion — no server-side session state or background threads. The
client (browser) holds the `device_code` between requests and passes it back to
the server for polling.

### Flow

1. **Client POSTs `/api/agateauth`**
   Server calls the Microsoft device authorization endpoint directly via HTTP and
   immediately returns an `AgateAuthStart` JSON object containing `DeviceCode`,
   `UserCode`, `VerificationUri`, `ExpiresIn`, `Interval`, and `Message`.
   The server then exits — no state retained.

2. **Client shows the user** the `UserCode` and `VerificationUri`
   (typically `https://microsoft.com/devicelogin`). User opens the URL in a browser,
   enters the code, and completes MFA.

3. **Client polls `GET /api/agateauth?dc={DeviceCode}`** every `Interval` seconds.
   Server calls the Microsoft token endpoint with the `device_code` and returns an
   `AgateAuthStatus` with `Status` of `"Pending"`, `"Ready"`, or `"Failed"`.
   On `"Ready"`, the response also includes `AccessToken` and `ExpiresIn`.

4. **Client stores the `AccessToken`** (in React state — intentionally ephemeral).
   Subsequent Agate requests (`POST /api/agate`) include `SessionToken` in the
   `AgateParams` JSON body.

5. **Server wraps the token** in an `AzureTokenFactory` that returns it directly
   for any scope request. When the token expires (~1 hour), the client will receive
   a failure from Agate and should restart the device code flow.

### Why Stateless?

The stateful alternative (server holds sessions + background polling threads) works
fine on a single machine but is hostile to restarts and multi-instance deployments.
The `device_code` is just an opaque string — safe to round-trip through the client
over HTTPS, short-lived (15 min), and only usable with the known `client_id`.

## Configuration

Set `AgateAuthType` to `"DeviceCode"` in the server config JSON. The existing
`Agate` config block supplies `AgateTenantId` and `AgateClientId` as usual.

```json
{
  "AgateAuthType": "DeviceCode",
  "Agate": {
    "AgateTenantId": "720cf133-4325-491c-b6a9-159d0497fc65",
    "AgateClientId":  "fdcf242b-a25b-4b35-aff2-d91d8100225d",
    ...
  }
}
```

The Agate app registration in Azure must have **"Allow public client flows"** enabled
(the same requirement as `UserPass`; if that was ever working, this will too).

## Files Changed

The device code logic lives entirely in `vdjlib` so that any server can implement
the two routes with minimal code — just HTTP plumbing that delegates to vdjlib.

| File | Change |
|------|--------|
| `vdjlib/.../AzureDeviceAuth.java` | **New.** All device code logic: `start()`, `check()`, `msPostForm()`, MS URL constants, MS response parsing classes (`MsDeviceCodeResp`, `MsTokenResp`), and the public result types `DeviceCodeChallenge` and `DeviceCodeStatus` |
| `vdjlib/.../AzureTokenFactory.java` | Added `DeviceCode` to `FactoryType` enum; added `createFromToken(String)` static factory that stores the token directly (bypasses `TokenCredential` entirely — no Reactor/Mono dependency) |
| `vdjlib/.../AgateImport.java` | Added `createDeviceCode(Config, String)` static factory |
| `standalone/.../Server.java` | New `AgateAuthScope` config; new `AgateDeviceCodeAuth` on `UserInfo`; new `SessionToken` on `AgateParams`; thin `handleAgateAuthRequest` / `startDeviceCodeFlow` / `checkDeviceCodeFlow` wrappers that delegate to `AzureDeviceAuth`; `agateDeviceAuthConfig()` helper to build `AzureDeviceAuth.Config` from `AgateImport.Config` |

### Implementing the routes in a different server

The contract is simple — two routes, each a handful of lines:

```java
// POST /agateauth
AzureDeviceAuth.Config authCfg = new AzureDeviceAuth.Config();
authCfg.TenantId = ...; authCfg.ClientId = ...; authCfg.Scope = ...; authCfg.TimeoutMillis = ...;
AzureDeviceAuth.DeviceCodeChallenge challenge = AzureDeviceAuth.start(authCfg);
// serialize challenge to JSON and return to client

// GET /agateauth?dc={deviceCode}
AzureDeviceAuth.DeviceCodeStatus status = AzureDeviceAuth.check(authCfg, deviceCode);
// serialize status to JSON and return to client
// when status.Status == "Ready", status.AccessToken is the bearer token

// In Agate request handlers, when auth type is DeviceCode:
AgateImport agate = AgateImport.createDeviceCode(agateCfg, sessionToken);
```

## Microsoft Endpoints Used

```
POST https://login.microsoftonline.com/{tenant}/oauth2/v2.0/devicecode
     client_id={agateClientId}&scope={ApiResource}

POST https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token
     grant_type=urn:ietf:params:oauth:grant-type:device_code
     &client_id={agateClientId}&device_code={deviceCode}
```

Note: Microsoft returns `authorization_pending` as HTTP 400 (not a network error),
so `msPostForm` reads the response body from `getErrorStream()` on 4xx responses.

## Token Refresh

There is no automatic refresh. Access tokens last ~1 hour. When one expires the
client will see Agate failures and needs to re-run the device code flow. This is
acceptable for the infrequent import use case this feature supports.
