# Deploying The Empty Valley to AWS (Mumbai, ap-south-1)

The production runbook for **theemptyvalley.com**, as built (AWS account `426197263227`, resources prefixed
`emptyvalley-`). It was done by hand in the console and CLI; move it to Terraform/CDK once it's stable. Everything
is in **ap-south-1**.

AWS refused CloudFront for this account on 2026-10-01 (it needs more usage and billing history). Until it's
allowed, the React build is on **Firebase Hosting** and the API has its own host. The details are in
`docs/DEPLOY-FRONTEND-FIREBASE.md`. §11 below keeps the CloudFront design for when we move back.

```
theemptyvalley.com, www ─▶ Firebase Hosting (CDN, SPA fallback; www → 301 → apex)
api.theemptyvalley.com  ─▶ ALB emptyvalley-alb (HTTPS, regional WAF) ─▶ ECS Fargate: 1 × backend container
                                                                       ├─ RDS PostgreSQL 16 (private)
                                                                       ├─ S3 emptyvalley-uploads-bucket
                                                                       ├─ SES (email) · MSG91 (SMS) · Razorpay
                                                                       └─ Secrets Manager
Razorpay webhooks ─▶ https://api.theemptyvalley.com/api/webhooks/razorpay
```

The site and the API are different origins on the same site. CORS allows the apex and www with credentials, and
the refresh cookie set by `api.` is still first-party. Run the CLI snippets in **bash**, after `aws login`, with
`export AWS_REGION=ap-south-1` (IAM calls fail with `NoRegion` otherwise; zsh mangles some of the expansions).

---

## 0. The slow things (they take days)
| What | Where | Status |
|---|---|---|
| Razorpay KYC + website review | Razorpay dashboard → Account & Settings | **Waiting on company registration.** Needs PAN, bank account, GST if registered, and live Terms / Privacy / Refunds / Contact / About pages |
| TRAI DLT registration | A DLT portal (Jio or Airtel are fastest), then link it in MSG91 | **Waiting on company registration** (needs business documents) |
| SES production access | SES console → Account dashboard | Granted 2026-09-28 (50k/day, 14/s) |
| AWS Activate credits | aws.amazon.com/activate | Not applied yet. Apply with the Workspace email now the site is live |
| Firebase Blaze plan | Firebase console → Usage and billing | Still on Spark. Switch before promoting the site (`DEPLOY-FRONTEND-FIREBASE.md` B1) |

Until DLT is approved, the backend runs `SMS_PROVIDER=log` and the build leaves `VITE_PHONE_OTP` unset, so the site
hides phone sign-in and people use Google or email. Once MSG91 delivers, set `SMS_PROVIDER=msg91` and add the
GitHub variable `VITE_PHONE_OTP=true` (passed to the build in `deploy.yml`).

## 1. AWS account hygiene
1. Enable **MFA on the root user**, then stop using root. Create a user in **IAM Identity Center** with
   `AdministratorAccess` for daily work. (Today the CLI still signs in as root through `aws login`.)
2. **Billing → Budgets**: a monthly cost budget with alerts at $50 and $100.
3. The account is billed by AWS India (AISPL).

## 2. Domain and DNS
`theemptyvalley.com` is registered at Spaceship, with DNS delegated to the Route 53 hosted zone
`Z0271334A30ZRAKKLWEE` (Spaceship DNSSEC off). Records that matter:

| Record | Value | Owner |
|---|---|---|
| apex `A` | `199.36.158.100` | Firebase Hosting |
| `www` `CNAME` | `the-empty-valley-da35c.web.app` | Firebase (redirects to the apex) |
| `api` `A` alias | `emptyvalley-alb` | backend |
| `origin` `A` alias | `emptyvalley-alb` | kept for CloudFront later |
| apex `MX` | `1 smtp.google.com.` | Google Workspace |
| apex `TXT` | two `google-site-verification` values (Workspace and Search Console), `v=spf1 include:_spf.google.com ~all`, `hosting-site=the-empty-valley-da35c` | shared: **edit, never replace** |
| `google._domainkey` | Workspace DKIM | Google Workspace |
| SES DKIM CNAMEs, `mail.` MX/TXT | SES Easy DKIM and MAIL FROM | SES |
| `_dmarc` `TXT` | `v=DMARC1; p=quarantine; pct=100;` (raised from `p=none` on 2026-10-05) | both mail senders pass DKIM |

## 3. Certificates (ACM, ap-south-1)
- `theemptyvalley.com`, `www`, `origin`: on the ALB listener.
- `api.theemptyvalley.com`: added to the same listener (SNI picks the right one).
- A us-east-1 cert for apex + www exists for CloudFront later. Firebase issues its own certificate for the site.

## 4. Network and security groups
The **default VPC** (`vpc-061465b7acc136dbb`). Its subnets are public, so Fargate tasks get a public IP and reach
ECR, SES, Razorpay and MSG91 without a NAT Gateway (about $35/mo saved).

| Security group | Inbound |
|---|---|
| `emptyvalley-alb` (`sg-06a5d338e337307ea`) | 443 from `0.0.0.0/0` (public API) and from the CloudFront prefix list `pl-9aa247f3` |
| `emptyvalley-app` (`sg-0fe37d814fe3c0cca`) | 8081 from `emptyvalley-alb` |
| `emptyvalley-db` (`sg-0afddd602b8afc091`) | 5432 from `emptyvalley-app` |

## 5. Database (RDS)
`emptyvalley-db`: PostgreSQL 16, `db.t4g.micro`, 20 GB gp3 (autoscaling to 1000 GB), private, SG `emptyvalley-db`.
- Endpoint `emptyvalley-db.ctg2aea22ho4.ap-south-1.rds.amazonaws.com`, database and master user `emptyvalley`.
- Password in the RDS-managed secret `rds!db-916da6f0-67fe-429c-975b-9bb9e85b2b7c` (key `password`).
- Automated backups 7 days (point-in-time restore), **deletion protection on**.
- RDS for PostgreSQL 16 enforces SSL, so the JDBC URL ends in `?sslmode=require`.
- Before a risky migration, take a manual snapshot (`emptyvalley-db-pre-<sha>`) and delete it once prod is fine.

## 6. S3 buckets
Both have **Block all public access** on:
- `emptyvalley-uploads-bucket`: avatars, trek photos and snow-report photos. Only the backend task role touches it;
  visitors get the files through `https://api.theemptyvalley.com/api/public/files/**`.
- `emptyvalley-web-bucket`: the React build for CloudFront later. Unused while Firebase serves the site.

## 7. Email (SES, ap-south-1)
- Domain identity `theemptyvalley.com` with Easy DKIM, custom MAIL FROM `mail.theemptyvalley.com`.
- The backend sends as `The Empty Valley <no-reply@theemptyvalley.com>` (`MAIL_PROVIDER=ses`). Verified end to end
  on 2026-10-05 with a password-reset email.
- The identity sends through its default configuration set **`my-first-configuration-set`**. SES checks
  `ses:SendEmail` on both the identity and that set (§10).
- **Bounces and complaints:**
  - Addresses that bounce or complain go on the account-level suppression list automatically.
  - The configuration set publishes `BOUNCE`, `COMPLAINT` and `REJECT` events to SNS **`emptyvalley-ses-events`**,
    which emails info@theemptyvalley.com.
  - The bounce and complaint *rates* have alarms (§16).
- Google Workspace (vikhilesh@ and info@theemptyvalley.com) sends the company's own mail; it has its own SPF and
  DKIM.

## 8. Secrets (Secrets Manager, ap-south-1)
- **`emptyvalley/prod`** (type *Other*):

  | Key | Value | Status |
  |---|---|---|
  | `JWT_SECRET` | output of `openssl rand -base64 48` | set |
  | `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | test keys first, live after activation | to add |
  | `RAZORPAY_WEBHOOK_SECRET` | the secret you type when registering the webhook (§14) | to add |
  | `MSG91_AUTH_KEY` | once DLT is done | to add |

- **`emptyvalley/origin-verify`**: the `X-Origin-Verify` value, for CloudFront later.
- The DB password stays in the RDS-managed secret.

When you add a key, also add it to the task definition's `secrets` (§10) and deploy.

## 9. Container registry
ECR repository `emptyvalley-backend` (private, scan on push), with images tagged by commit SHA. CI pushes them
(§13). By hand:

```bash
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin 426197263227.dkr.ecr.ap-south-1.amazonaws.com
docker build --platform linux/amd64 -t 426197263227.dkr.ecr.ap-south-1.amazonaws.com/emptyvalley-backend:<sha> backend
docker push 426197263227.dkr.ecr.ap-south-1.amazonaws.com/emptyvalley-backend:<sha>
```
The Dockerfile's build stage runs on `--platform=$BUILDPLATFORM`. Emulating amd64 on an arm64 Mac breaks `tar`.

## 10. Backend on ECS Fargate
### IAM roles
- **`emptyvalley-task-execution`** (ECS uses it to start the container): `AmazonECSTaskExecutionRolePolicy`, plus
  `secretsmanager:GetSecretValue` on `emptyvalley/prod*` and the `rds!db-*` secret.
- **`emptyvalley-task`** (the app itself), inline policy `uploads-and-mail`:
  - `s3:GetObject`, `s3:PutObject` and `s3:DeleteObject` on `arn:aws:s3:::emptyvalley-uploads-bucket/*`;
  - `s3:ListBucket` on the bucket, so a missing file is a 404 rather than a 403;
  - `ses:SendEmail` and `ses:SendRawEmail` on the SES identity **and** on `configuration-set/my-first-configuration-set`.

### Task definition `emptyvalley-backend`
- **Launch type:** Fargate, Linux/X86_64, **0.5 vCPU / 1 GB**, both roles above.
- **Container `backend`:** port **8081**, log driver `awslogs`, log group `/ecs/emptyvalley-backend` with 30-day retention.
- **Environment variables (live):**

  | Name | Value |
  |---|---|
  | `DB_URL` | `jdbc:postgresql://emptyvalley-db.ctg2aea22ho4.ap-south-1.rds.amazonaws.com:5432/emptyvalley?sslmode=require` |
  | `DB_USER` | `emptyvalley` |
  | `DB_POOL_SIZE` | `10` |
  | `PUBLIC_BASE_URL` | `https://api.theemptyvalley.com` (photo URLs are built from it) |
  | `FRONTEND_BASE_URL` | `https://theemptyvalley.com` (links in emails) |
  | `CORS_ALLOWED_ORIGINS` | `https://theemptyvalley.com,https://www.theemptyvalley.com` |
  | `AUTH_COOKIE_SECURE` | `true` |
  | `GOOGLE_CLIENT_ID` | `864514738729-9l55utcre1blaf6bf88440igo6jkv8kl.apps.googleusercontent.com` |
  | `STORAGE_TYPE` | `s3` |
  | `S3_UPLOADS_BUCKET` | `emptyvalley-uploads-bucket` |
  | `AWS_REGION` | `ap-south-1` |
  | `MAIL_PROVIDER` | `ses` |
  | `MAIL_FROM` | `The Empty Valley <no-reply@theemptyvalley.com>` |
  | `SMS_PROVIDER` | `log` until DLT is done, then `msg91` |
  | `MSG91_OTP_TEMPLATE_ID` | from MSG91, once DLT is done |
  | `RATE_LIMIT_CLIENT_IP_HEADER` | `X-Forwarded-For` (`CloudFront-Viewer-Address` once on CloudFront) |
  | `ADMIN_EMAILS` | `vikhilesh2411@gmail.com` |
  | `GUIDE_SHARE_BPS` | `7000` |

- **Secrets** (`valueFrom`): `JWT_SECRET` from `emptyvalley/prod` and `DB_PASSWORD` from the RDS secret's `password`
  key. Once you have them, add `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` and
  `MSG91_AUTH_KEY` from `emptyvalley/prod`.
- **Changing env:** create a new revision of the live task definition and update the service to it. CI copies the
  live revision on every deploy and swaps only the image, so env changes survive deploys.

### Load balancer `emptyvalley-alb`
- Application, internet-facing, default VPC, SG `emptyvalley-alb`. The `X-Forwarded-For` mode is the default,
  `append`, so the ALB adds the client IP as the **last** entry. `RateLimitFilter` trusts only that entry.
- **Target group `emptyvalley-backend`:**
  - type **IP**, HTTP 8081;
  - health check `/api/public/health`, healthy threshold 2, interval 15 s;
  - deregistration delay 30 s.
- **Listener HTTPS:443** (certs from §3). The **default action returns a fixed 403.**
  - **Rule 10:** header `X-Origin-Verify` = the `emptyvalley/origin-verify` value → backend (for CloudFront later).
  - **Rule 20:** host `api.theemptyvalley.com` → backend.

### Cluster and service
- Cluster `emptyvalley` (Fargate only), service `emptyvalley-backend`:
  - **desired tasks 1**;
  - deployment min 100% / max 200%, **circuit breaker with rollback**;
  - default VPC subnets, SG `emptyvalley-app`, public IP on;
  - health-check grace period 120 s (Spring plus Flyway needs time to start).
- Flyway migrates on startup. Watch it in the log group.

### WAF (regional web ACL `emptyvalley-alb`, attached to the ALB)
| Priority | Rule | Action |
|---|---|---|
| 0 | `common`: `AWSManagedRulesCommonRuleSet`. `SizeRestrictions_BODY` → Count (uploads are up to 5 MB). `CrossSiteScripting_BODY` → Count (it mistakes bytes inside JPEGs for script) | Block |
| 1 | `xss-body-except-uploads`: blocks requests carrying the `CrossSiteScripting_BODY` label, **except** multipart uploads to track photos, snow-report photos (admin and guide) and `/api/account/avatar` | Block |
| 2 | `bad-inputs`: `AWSManagedRulesKnownBadInputsRuleSet` | Block |
| 3 | `api-rate`: 1000 requests / 5 min per IP on `/api/*`. Looser than usual because Indian mobile carriers put many users behind one IP (CGNAT) | Block |

## 11. CloudFront (not live: AWS hasn't allowed it yet)
This was the original design, and it's where we go once AWS verifies the account. The steps to switch are in
`DEPLOY-FRONTEND-FIREBASE.md` Part D. Already created: the function `emptyvalley-spa`, OAC `emptyvalley-web-oac`,
origin request policy `emptyvalley-api` and the us-east-1 cert. The us-east-1 WAF was deleted; recreate it.

### CloudFront Function `emptyvalley-spa` (viewer request, default behaviour only)
It redirects `www` to the apex and serves `index.html` for SPA routes. Don't use "custom error responses" for SPA
routing: they would also turn the API's JSON 404s into `index.html`.

```js
function handler(event) {
  var req = event.request;
  var host = req.headers.host && req.headers.host.value;
  if (host && host.indexOf('www.') === 0) {
    return { statusCode: 301, statusDescription: 'Moved Permanently',
             headers: { location: { value: 'https://' + host.slice(4) + req.uri } } };
  }
  if (req.uri.indexOf('.') === -1) { req.uri = '/index.html'; }  // /treks/x, /account/bookings/y, …
  return req;
}
```

### Distribution
- **Origins:**
  - S3 `emptyvalley-web-bucket` through OAC. The bucket policy's `SourceArn` is the distribution.
  - `origin.theemptyvalley.com`: HTTPS only, custom header `X-Origin-Verify: <emptyvalley/origin-verify>`.
- **Default behaviour `/*` → S3:**
  - redirect HTTP to HTTPS;
  - cache policy `CachingOptimized`;
  - function `emptyvalley-spa` on viewer request.
- **Behaviour `/api/*` → origin:**
  - redirect HTTP to HTTPS, all methods;
  - cache policy **`CachingDisabled`**;
  - origin request policy `emptyvalley-api`: all viewer headers plus **`CloudFront-Viewer-Address`**, all cookies,
    all query strings. Set `RATE_LIMIT_CLIENT_IP_HEADER=CloudFront-Viewer-Address`.
- **Settings:**
  - aliases apex and www;
  - the us-east-1 cert;
  - price class including India;
  - default root object `index.html`;
  - a us-east-1 WAF with the same rules as §10.

## 12. Rate limiting, both layers
| Layer | What it stops | Config |
|---|---|---|
| WAF (`emptyvalley-alb`) | floods, bots, known-bad inputs | §10 |
| App `RateLimitFilter` | credential stuffing, OTP/SMS abuse, guest-booking spam; answers `429 RATE_LIMITED` | `app.rate-limit.*` in `application.yml` |
| App domain throttles | per-email login lockout, per-phone OTP cooldown | `LoginAttemptLimiter`, `OtpService` |

The app buckets live in memory, which is correct for one task. Before running more than one task, move them to
Postgres or Redis.

## 13. CI/CD (GitHub Actions)
`.github/workflows/ci.yml` runs the tests on every PR. `deploy.yml` deploys `main` once CI passes: the backend to
ECS (Flyway migrates on startup), then the frontend to Firebase Hosting (docs/DEPLOY-FRONTEND-FIREBASE.md).
GitHub signs in to AWS and Google through OIDC, so no keys are stored. Both trust only the `production`
environment of this repo. The repo uses GitHub's **immutable** OIDC subject, which carries the owner and repo IDs:
`repo:thelostkid24@107846176/trek@1382027667:environment:production` (check with
`gh api repos/thelostkid24/trek/actions/oidc/customization/sub`). A plain `repo:thelostkid24/trek:…` subject is
refused. One-time setup:

1. **AWS** (done 2026-10-02): IAM identity provider `token.actions.githubusercontent.com` (audience
   `sts.amazonaws.com`) and role **`emptyvalley-github-deploy`**:
   - Trust: that provider, with `aud` = `sts.amazonaws.com` and `sub` = the immutable subject above.
   - Inline policy `deploy`:
     - ECR login and push to `emptyvalley-backend`;
     - `ecs:DescribeTaskDefinition` and `ecs:RegisterTaskDefinition`;
     - `ecs:UpdateService` and `DescribeServices` on `emptyvalley/emptyvalley-backend`;
     - `iam:PassRole` on `emptyvalley-task-execution` and `emptyvalley-task` (to ECS only).
2. **Google** (done 2026-10-02; Workload Identity Federation, needs the `gcloud` CLI logged in as a project owner):
   ```bash
   PROJECT=the-empty-valley-da35c
   PROJECT_NUMBER=$(gcloud projects describe $PROJECT --format='value(projectNumber)')
   SA=github-deploy@$PROJECT.iam.gserviceaccount.com
   SUBJECT='repo:thelostkid24@107846176/trek@1382027667:environment:production'
   gcloud services enable iamcredentials.googleapis.com sts.googleapis.com --project $PROJECT
   gcloud iam service-accounts create github-deploy --display-name "GitHub deploy" --project $PROJECT
   for ROLE in roles/firebasehosting.admin roles/serviceusage.serviceUsageConsumer; do
     gcloud projects add-iam-policy-binding $PROJECT --member serviceAccount:$SA --role $ROLE --condition None
   done
   gcloud iam workload-identity-pools create github --location global --display-name GitHub --project $PROJECT
   gcloud iam workload-identity-pools providers create-oidc github --location global --workload-identity-pool github \
     --issuer-uri https://token.actions.githubusercontent.com \
     --attribute-mapping google.subject=assertion.sub,attribute.repository=assertion.repository \
     --attribute-condition "assertion.sub == '$SUBJECT'" --project $PROJECT
   gcloud iam service-accounts add-iam-policy-binding $SA --role roles/iam.workloadIdentityUser --project $PROJECT \
     --member "principal://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/subject/$SUBJECT"
   echo "GCP_WORKLOAD_IDENTITY_PROVIDER=projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/github"
   echo "GCP_SERVICE_ACCOUNT=$SA"
   ```
3. GitHub → Settings → Environments → **production** (exists; add required reviewers to approve each deploy).
4. **Repository variables** (Settings → Secrets and variables → Actions → Variables):

   | Variable | Value |
   |---|---|
   | `AWS_ROLE_ARN` | `arn:aws:iam::426197263227:role/emptyvalley-github-deploy` |
   | `ECR_REPOSITORY` | `emptyvalley-backend` |
   | `ECS_CLUSTER` | `emptyvalley` |
   | `ECS_SERVICE` | `emptyvalley-backend` |
   | `ECS_TASK_FAMILY` | `emptyvalley-backend` |
   | `ECS_CONTAINER` | `backend` |
   | `VITE_API_BASE_URL` | `https://api.theemptyvalley.com` |
   | `VITE_GOOGLE_CLIENT_ID` | the OAuth client id |
   | `FIREBASE_PROJECT` | `the-empty-valley-da35c` |
   | `GCP_WORKLOAD_IDENTITY_PROVIDER` | printed by step 2 |
   | `GCP_SERVICE_ACCOUNT` | printed by step 2 |
   | `VITE_PHONE_OTP` | not set (phone sign-in hidden); `true` once SMS delivers |

To redeploy without a new commit: Actions → Deploy → **Run workflow**.

## 14. Third parties
- **Razorpay** → Webhooks → `https://api.theemptyvalley.com/api/webhooks/razorpay`, with:
  - events `payment.captured`, `order.paid`, `payment.failed`, `refund.processed`, `refund.failed`;
  - secret = `RAZORPAY_WEBHOOK_SECRET`.

  Do this once in **test** mode and again in **live** mode; each mode has its own webhooks and keys.
- **Google OAuth client** (Cloud project `the-empty-valley`):
  - Authorised JavaScript origins: the apex, www and localhost.
  - The consent screen needs the home, privacy and terms URLs on its Branding page before **Publish app**.

## 15. Go live
1. Add Razorpay **test** keys (§8, §10) and deploy. This stack is your staging until launch.
2. Run through it end to end:
   - sign up and get the verification email;
   - book, pay with a test card, get the confirmation email, and confirm the webhook shows up in the logs;
   - cancel and see the refund;
   - hammer `/api/auth/login` until it returns 429.
3. Finish `frontend/src/lib/business.ts` (address, support phone, grievance officer), have the Terms and Privacy
   pages reviewed, then submit the site to Razorpay.
4. Swap in the live keys and the live webhook secret, then redeploy (ECS → Update service → Force new deployment).
5. Make a real ₹1 test departure, book it, then cancel and refund it.

## 16. Operating it
- **Logs:** CloudWatch → Log groups → `/ecs/emptyvalley-backend` (30 days).
- **Alerts:** every alarm emails **info@theemptyvalley.com** through SNS `emptyvalley-alarms` (ap-south-1), when it
  fires and again when it clears. The email subscription has to be confirmed once from that inbox.

  | Alarm (ap-south-1) | Fires when |
  |---|---|
  | `emptyvalley-api-no-healthy-task` | no healthy backend task for 3 min (the API is down; missing data counts as down) |
  | `emptyvalley-api-unhealthy` | a task fails its health check for 3 min |
  | `emptyvalley-api-5xx` | more than 5 server errors in 5 min |
  | `emptyvalley-api-cpu` / `-memory` | backend CPU or memory > 85% for 15 min |
  | `emptyvalley-db-cpu` | RDS CPU > 80% for 15 min |
  | `emptyvalley-db-storage` | RDS free storage < 2 GB |
  | `emptyvalley-db-connections` | more than 60 DB connections (a t4g.micro allows about 80) |
  | `emptyvalley-ses-bounce-rate` | SES bounce rate > 5% (AWS reviews the account at 5%, pauses sending at 10%) |
  | `emptyvalley-ses-complaint-rate` | SES complaint rate > 0.1% (AWS reviews the account at 0.1%) |

- **Uptime:** `.github/workflows/uptime.yml` runs daily at 09:00 IST (and on demand: Actions → Uptime → Run
  workflow). It fetches `https://api.theemptyvalley.com/api/public/health` and `https://theemptyvalley.com/`,
  retrying 3 times. A failed run makes GitHub email whoever last changed that file's schedule. It's free. Once
  traffic justifies it, switch to Route 53 HTTPS health checks (30 s interval, ~$1–1.50 a month each) with a
  `HealthCheckStatus` alarm in us-east-1. Between daily runs, `emptyvalley-api-no-healthy-task` still catches the
  API going down within minutes.
- **Rollback:**
  - The circuit breaker rolls back failed deploys automatically. To roll back by hand, update the service to the
    previous task definition revision.
  - The frontend rolls back from Firebase → Hosting → release history.
  - A migration can't be rolled back. Keep migrations additive, and never edit an applied one.
- **Database restore:** RDS → Restore to point in time. This creates a new instance; point `DB_URL` at it.
- **Scaling up:**
  1. First, raise the task size (1 vCPU / 2 GB).
  2. Before running more than one task:
     - add ShedLock, so `PaymentReconciler` and `DepartureLifecycleJob` run once;
     - move the rate-limit buckets to a shared store.
  3. Then raise the desired count or add target-tracking autoscaling on CPU.
  4. Move RDS to Multi-AZ when downtime starts costing money.
- **No Kubernetes.** EKS costs $73/mo for the control plane alone and adds ops work this app doesn't need.

## Monthly cost (approx., 2026)
| Item | USD |
|---|---|
| Fargate 0.5 vCPU / 1 GB, 24×7 | ~18 |
| Application Load Balancer | ~18–20 |
| RDS db.t4g.micro + 20 GB gp3 + backups | ~15–18 |
| WAF (regional ACL + rules + requests) | ~8–10 |
| Route 53 zone, S3, Secrets Manager, CloudWatch alarms | ~3–5 |
| Firebase Hosting | free on Spark; small usage charges on Blaze |
| **Total** | **~65–75** (₹5.5–6.5k) |

SES costs about $0.10 per 1,000 emails, and MSG91 about ₹0.15–0.25 per OTP SMS.
