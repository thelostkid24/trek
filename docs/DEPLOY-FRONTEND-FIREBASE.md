# Frontend on Firebase Hosting, API on AWS (until CloudFront)

AWS refused CloudFront for the account (2026-10-01: needs more usage and billing history). Until we reapply, the
React build is served by **Firebase Hosting** (Google's global CDN). Everything else stays on AWS.

```
theemptyvalley.com, www  ─▶ Firebase Hosting (CDN, SPA fallback)      www → 301 → apex
api.theemptyvalley.com   ─▶ ALB emptyvalley-alb (HTTPS, regional WAF) ─▶ ECS emptyvalley-backend ─▶ RDS
```

The site and the API are now **different origins on the same site**, the way local dev already runs
(5173 → 8081). Nothing in the code changes; only config:

| Where | Setting | Value |
|---|---|---|
| Frontend build | `VITE_API_BASE_URL` | `https://api.theemptyvalley.com` |
| Frontend build | `VITE_GOOGLE_CLIENT_ID` | `864514738729-9l55utcre1blaf6bf88440igo6jkv8kl.apps.googleusercontent.com` |
| Backend task def | `CORS_ALLOWED_ORIGINS` | `https://theemptyvalley.com,https://www.theemptyvalley.com` |
| Backend task def | `AUTH_COOKIE_SECURE` | `true` |
| Backend task def | `PUBLIC_BASE_URL` | `https://api.theemptyvalley.com` (avatar/photo URLs are built from it) |
| Backend task def | `FRONTEND_BASE_URL` | `https://theemptyvalley.com` (links in emails) |
| Backend task def | `RATE_LIMIT_CLIENT_IP_HEADER` | `X-Forwarded-For` (see A4) |

The refresh cookie (`HttpOnly; Secure; SameSite=Lax; Path=/api/auth`) is set by `api.theemptyvalley.com` and is
sent on the frontend's `credentials: 'include'` calls, because `theemptyvalley.com` and `api.theemptyvalley.com`
are the same *site*. The CORS config already allows credentials.

Run the AWS blocks in **bash** from the repo root, after `aws login`:
```bash
export AWS_REGION=ap-south-1
ALB_ARN=arn:aws:elasticloadbalancing:ap-south-1:426197263227:loadbalancer/app/emptyvalley-alb/762ca19eca8af01a
LISTENER=arn:aws:elasticloadbalancing:ap-south-1:426197263227:listener/app/emptyvalley-alb/762ca19eca8af01a/c9b4d0e8cd454515
ALB_SG=sg-06a5d338e337307ea
ZONE=Z0271334A30ZRAKKLWEE
```

---

## Part A: make the API public at api.theemptyvalley.com (AWS, one-time)

### A1. Certificate for api.theemptyvalley.com
The ap-south-1 cert covers apex, www and origin, not `api`. Request one and validate it through Route 53:
```bash
API_CERT=$(aws acm request-certificate --domain-name api.theemptyvalley.com --validation-method DNS \
  --query CertificateArn --output text)
sleep 10
aws acm describe-certificate --certificate-arn $API_CERT \
  --query 'Certificate.DomainValidationOptions[0].ResourceRecord'
```
Create that CNAME in the hosted zone (console: ACM → the cert → **Create records in Route 53** does it in one
click), then:
```bash
aws acm wait certificate-validated --certificate-arn $API_CERT
aws elbv2 add-listener-certificates --listener-arn $LISTENER --certificates CertificateArn=$API_CERT
```
The listener picks the right cert per hostname (SNI).

### A2. Route api.theemptyvalley.com to the backend
The default action stays the 403, and rule 10 (`X-Origin-Verify` → backend) stays for CloudFront later.
```bash
BACKEND_TG=$(aws elbv2 describe-target-groups --names emptyvalley-backend \
  --query 'TargetGroups[0].TargetGroupArn' --output text)
aws elbv2 create-rule --listener-arn $LISTENER --priority 20 \
  --conditions Field=host-header,Values=api.theemptyvalley.com \
  --actions Type=forward,TargetGroupArn=$BACKEND_TG
```

### A3. DNS for api
Route 53 → `theemptyvalley.com` → create record `api`, type **A**, alias to Application Load Balancer →
ap-south-1 → `emptyvalley-alb`. The ALB is IPv4-only, so skip AAAA.

### A4. Real client IP for the rate limiter
Without CloudFront there's no `CloudFront-Viewer-Address`; the backend would see only the ALB's private IP and
put every visitor in **one** rate-limit bucket. The ALB's default `X-Forwarded-For` mode, `append`, adds the
client IP as the **last** entry (there is no `replace` mode), and `RateLimitFilter.clientIp` trusts only that
last entry, so a client can't pick its own bucket by sending the header itself. Check the mode is `append`:
```bash
aws elbv2 describe-load-balancer-attributes --load-balancer-arn $ALB_ARN \
  --query "Attributes[?Key=='routing.http.xff_header_processing.mode'].Value" --output text
```

### A5. Backend environment
ECS → Task definitions → `emptyvalley-backend` → **Create new revision** → container env: set every
"Backend task def" row from the table at the top. Save, then ECS → service `emptyvalley-backend` → **Update** →
new revision → deploy, and wait for it to be healthy.

### A6. Regional WAF on the ALB
The `emptyvalley-web` web ACL is CloudFront-scoped (us-east-1) and can't attach to an ALB. Delete it (it bills
monthly and protects nothing), then in WAF & Shield → **ap-south-1** → Web ACLs → Create:
- name `emptyvalley-alb`, resource type **Regional**, associate `emptyvalley-alb`;
- managed rules `AWSManagedRulesCommonRuleSet` (set `SizeRestrictions_BODY` to **Count**, or avatar uploads over
  8 KB break) and `AWSManagedRulesKnownBadInputsRuleSet`;
- rate-based rule: 1000 requests / 5 min per IP, URI path starts with `/api/`, action Block.

### A7. Open the ALB to the internet
Only after A2–A6. Until now port 443 only admitted CloudFront's prefix list.
```bash
aws ec2 authorize-security-group-ingress --group-id $ALB_SG --ip-permissions \
  'IpProtocol=tcp,FromPort=443,ToPort=443,IpRanges=[{CidrIp=0.0.0.0/0,Description=public api (no CloudFront)}]'
```

### A8. Check the API
```bash
curl -s https://api.theemptyvalley.com/api/public/health
curl -sI -X OPTIONS https://api.theemptyvalley.com/api/auth/refresh \
  -H 'Origin: https://theemptyvalley.com' -H 'Access-Control-Request-Method: POST' \
  | grep -i '^access-control-allow'   # origin = https://theemptyvalley.com, credentials = true
curl -s -o /dev/null -w '%{http_code}\n' https://origin.theemptyvalley.com/api/public/health  # still 403
```

---

## Part B: Firebase Hosting (one-time)

### B1. Project and billing
Firebase project **The Empty Valley**, ID `the-empty-valley-da35c`, under the `theemptyvalley.com` organization
(created 2026-10-02). Temporary URL: https://the-empty-valley-da35c.web.app.

It runs on the free **Spark** plan. Spark caps transfer at 360 MB/day; a first visit is ~1–1.5 MB (mostly the
hero photos), so a few hundred new visitors a day would take the site offline until midnight (Pacific). **Before
promoting the site**, switch to **Blaze**: Google India asks for a one-time ₹3,000 prepayment, credited to the
billing account and spent against usage. Then add a budget alert (Google Cloud → Billing → Budgets & alerts).

### B2. First deploy
`frontend/firebase.json` holds the config: SPA fallback to `index.html`, `immutable` caching on Vite's hashed
`/assets/` (the landing photos live there too, via `src/assets`), `no-cache` on everything else, root images included, so a release shows up at once. When header
rules overlap, Firebase lets the **later** rule win, so the catch-all comes first.

Deploy what's on `main`, not a working branch whose backend changes aren't live yet. Vite reads the repo-root `.env`, but
variables set on the command line win, so the localhost values there don't leak into the build.
```bash
cd frontend
npx -y firebase-tools@latest login
VITE_API_BASE_URL=https://api.theemptyvalley.com \
VITE_GOOGLE_CLIENT_ID=864514738729-9l55utcre1blaf6bf88440igo6jkv8kl.apps.googleusercontent.com \
  npm run build
grep -o 'https://api.theemptyvalley.com' dist/assets/*.js | head -1   # must print the API URL
npx -y firebase-tools@latest deploy --only hosting --project the-empty-valley-da35c
```
The deploy prints `https://the-empty-valley-da35c.web.app`. The site loads there, but API calls fail CORS until the custom domain is
live. That's expected.

### B3. Custom domain
Firebase → Hosting → **Add custom domain** → `theemptyvalley.com`. Then add `www.theemptyvalley.com` and choose
**Redirect to theemptyvalley.com**. For each, add **exactly** the records the wizard shows to the Route 53
zone. Watch out for:
- **The apex TXT record is shared.** Route 53 keeps one TXT record set per name, and the apex one already holds
  `google-site-verification=…` and `v=spf1 include:_spf.google.com ~all`. **Edit** it and add Firebase's value
  as an extra line. Never replace it, or Workspace mail and SPF break.
- Leave the apex **MX** and the `google._domainkey`, `mail.`, `_dmarc` and SES DKIM records alone.
- If the zone has a **CAA** record, it must allow `pki.goog` and `letsencrypt.org` or Firebase can't issue the
  certificate.
- Remove any other A/AAAA/CNAME on the apex or www that the wizard flags as conflicting.

The certificate takes from a few minutes to a few hours. The wizard shows **Connected** when it's done.

### B4. Check the site
```bash
curl -sI https://theemptyvalley.com/ | grep -i -E '^(HTTP|cache-control)'          # 200, no-cache
curl -sI https://theemptyvalley.com/treks/anything | head -1                         # 200 (SPA fallback)
curl -sI "https://theemptyvalley.com$(curl -s https://theemptyvalley.com/ | grep -o '/assets/[^"]*\.js' | head -1)" \
  | grep -i cache-control                                                            # immutable
curl -sI https://www.theemptyvalley.com/login | grep -i location                     # https://theemptyvalley.com/login
```
In a browser: sign up, log out and back in, **reload the page while logged in** (this exercises the refresh
cookie across origins), sign in with Google, and open a page with a guide photo.

### B5. Google Search Console (docs/TRD.md §7.16)
```bash
curl -s https://theemptyvalley.com/robots.txt | head -3                             # plain text, not index.html
curl -s https://api.theemptyvalley.com/api/public/sitemap.xml | head -4             # <urlset …>
```
1. Open https://search.google.com/search-console signed in as `vikhilesh@theemptyvalley.com` → **Add property** →
   **Domain** → `theemptyvalley.com`. The apex already has a `google-site-verification` TXT record from the Workspace
   setup, so it may verify straight away. If it doesn't, add the TXT value it shows to the Route 53 apex TXT record
   (keep the existing values).
2. **Sitemaps** → submit `https://api.theemptyvalley.com/api/public/sitemap.xml`. It's on the API host, which is
   allowed because the site's robots.txt names it.
3. **URL Inspection** → `https://theemptyvalley.com/` and one trek page → **Test live URL** → check the rendered
   HTML has the page's own `<title>` and canonical link → **Request indexing**.
4. Optional: Bing Webmaster Tools → **Import from Google Search Console**.

---

## Part C: every frontend release
```bash
cd frontend
VITE_API_BASE_URL=https://api.theemptyvalley.com \
VITE_GOOGLE_CLIENT_ID=864514738729-9l55utcre1blaf6bf88440igo6jkv8kl.apps.googleusercontent.com \
  npm run build
npx -y firebase-tools@latest deploy --only hosting --project the-empty-valley-da35c
```
Firebase swaps the whole site at once and purges its CDN. **Rollback:** Firebase → Hosting → release history →
**Rollback** on the previous release.

Normally you don't run this by hand: the GitHub `Deploy` workflow builds and deploys the frontend after the
backend on every push to `main` (setup in docs/DEPLOY.md §13). The commands above are the manual fallback.

When the API is published, Razorpay's webhook URL is `https://api.theemptyvalley.com/api/webhooks/razorpay`.

---

## Part D: moving to CloudFront later
Once AWS allows CloudFront, create the distribution as in docs/DEPLOY.md §11 (the S3 bucket
`emptyvalley-web-bucket` and the OAC, function and origin request policy already exist; recreate the
us-east-1 WAF with the regional ACL's rules, docs/DEPLOY.md §10). Then:
1. Build with `VITE_API_BASE_URL=''`, upload to S3, and point the Route 53 apex and www records at the
   distribution (A + AAAA, replacing Firebase's records and removing its TXT line).
2. Put `FRONTEND_BASE_URL`, `PUBLIC_BASE_URL` and `CORS_ALLOWED_ORIGINS` back to the apex, and set
   `RATE_LIMIT_CLIENT_IP_HEADER` back to `CloudFront-Viewer-Address`.
3. Remove the ALB's `0.0.0.0/0` rule (CloudFront prefix list only), listener rule 20, the `api` cert, the `api`
   DNS record and the regional WAF. Move the Razorpay webhook to the apex.
4. Point the `frontend` job in `.github/workflows/deploy.yml` back at S3 + CloudFront invalidation (it did that
   before 2026-10-02; see git history) and set `VITE_API_BASE_URL` to empty.
5. Delete the Firebase Hosting site, or downgrade the project to Spark.

Avatars and trek photos already uploaded keep working: their URLs are built from `PUBLIC_BASE_URL` on every
read, not stored.
