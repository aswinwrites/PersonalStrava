// supabase/functions/send-report/index.ts
//
// Deterministic weekly/monthly movement report, emailed via Resend.
// NO AI. NO generated prose. Every number here is a straight aggregate or
// arithmetic comparison — see spec section 33 and docs/exports.md.
//
// Invocation: scheduled by a Supabase cron trigger (pg_cron -> pg_net, or an
// external scheduler hitting this URL) with a JSON body: { "period": "weekly" | "monthly" }.
// Auth: called with the service role key server-side only — never exposed to
// clients. This function reads report preferences from `profiles` for every
// user with weekly_report_enabled/monthly_report_enabled = true.

import { createClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const RESEND_API_KEY = Deno.env.get("RESEND_API_KEY")!;
const RESEND_FROM = Deno.env.get("RESEND_FROM_ADDRESS") ?? "reports@personalstrava.app";

type Period = "weekly" | "monthly";

interface PeriodBounds {
  start: string; // ISO date, inclusive
  end: string; // ISO date, inclusive
  prevStart: string;
  prevEnd: string;
  label: string;
}

function computeBounds(period: Period, now = new Date()): PeriodBounds {
  const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));

  if (period === "weekly") {
    // ISO week: Monday - Sunday, reporting the week that just ended.
    const day = d.getUTCDay() === 0 ? 7 : d.getUTCDay();
    const lastSunday = new Date(d);
    lastSunday.setUTCDate(d.getUTCDate() - day);
    const lastMonday = new Date(lastSunday);
    lastMonday.setUTCDate(lastSunday.getUTCDate() - 6);

    const prevSunday = new Date(lastMonday);
    prevSunday.setUTCDate(lastMonday.getUTCDate() - 1);
    const prevMonday = new Date(prevSunday);
    prevMonday.setUTCDate(prevSunday.getUTCDate() - 6);

    return {
      start: lastMonday.toISOString().slice(0, 10),
      end: lastSunday.toISOString().slice(0, 10),
      prevStart: prevMonday.toISOString().slice(0, 10),
      prevEnd: prevSunday.toISOString().slice(0, 10),
      label: `Week of ${lastMonday.toISOString().slice(0, 10)}`,
    };
  }

  // monthly: the month that just ended
  const firstOfThisMonth = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1));
  const lastMonthEnd = new Date(firstOfThisMonth);
  lastMonthEnd.setUTCDate(0);
  const lastMonthStart = new Date(Date.UTC(lastMonthEnd.getUTCFullYear(), lastMonthEnd.getUTCMonth(), 1));

  const prevMonthEnd = new Date(lastMonthStart);
  prevMonthEnd.setUTCDate(0);
  const prevMonthStart = new Date(Date.UTC(prevMonthEnd.getUTCFullYear(), prevMonthEnd.getUTCMonth(), 1));

  return {
    start: lastMonthStart.toISOString().slice(0, 10),
    end: lastMonthEnd.toISOString().slice(0, 10),
    prevStart: prevMonthStart.toISOString().slice(0, 10),
    prevEnd: prevMonthEnd.toISOString().slice(0, 10),
    label: lastMonthStart.toLocaleString("en-US", { month: "long", year: "numeric", timeZone: "UTC" }),
  };
}

function fmtKm(meters: number) {
  return (meters / 1000).toFixed(1);
}

function fmtHM(seconds: number) {
  const h = Math.floor(seconds / 3600);
  const m = Math.round((seconds % 3600) / 60);
  return `${h}h ${m}m`;
}

function pctChange(current: number, previous: number): string {
  if (previous === 0) return current > 0 ? "+∞%" : "0%";
  const pct = ((current - previous) / previous) * 100;
  return `${pct >= 0 ? "+" : ""}${pct.toFixed(0)}%`;
}

interface DailyStatsRow {
  date: string;
  steps: number;
  walking_distance_meters: number;
  cycling_distance_meters: number;
  motorcycling_distance_meters: number;
  walking_seconds: number;
  cycling_seconds: number;
  motorcycling_seconds: number;
  elevation_gain_meters: number;
  activity_count: number;
}

function sumStats(rows: DailyStatsRow[]) {
  return rows.reduce(
    (acc, r) => ({
      steps: acc.steps + r.steps,
      walkingDistance: acc.walkingDistance + r.walking_distance_meters,
      cyclingDistance: acc.cyclingDistance + r.cycling_distance_meters,
      motorcyclingDistance: acc.motorcyclingDistance + r.motorcycling_distance_meters,
      walkingSeconds: acc.walkingSeconds + r.walking_seconds,
      cyclingSeconds: acc.cyclingSeconds + r.cycling_seconds,
      motorcyclingSeconds: acc.motorcyclingSeconds + r.motorcycling_seconds,
      elevationGain: acc.elevationGain + r.elevation_gain_meters,
      activityCount: acc.activityCount + r.activity_count,
    }),
    {
      steps: 0,
      walkingDistance: 0,
      cyclingDistance: 0,
      motorcyclingDistance: 0,
      walkingSeconds: 0,
      cyclingSeconds: 0,
      motorcyclingSeconds: 0,
      elevationGain: 0,
      activityCount: 0,
    },
  );
}

function renderEmailHtml(displayName: string, period: Period, bounds: PeriodBounds, current: ReturnType<typeof sumStats>, previous: ReturnType<typeof sumStats>) {
  const totalDistance = current.walkingDistance + current.cyclingDistance + current.motorcyclingDistance;
  const totalTime = current.walkingSeconds + current.cyclingSeconds + current.motorcyclingSeconds;
  const heading = period === "weekly" ? `YOUR ${bounds.label.toUpperCase()} MOVEMENT REPORT` : `YOUR ${bounds.label.toUpperCase()} MOVEMENT REPORT`;

  return `<!doctype html>
<html>
<body style="font-family: -apple-system, Helvetica, Arial, sans-serif; background:#0b0b0c; color:#f2f2f2; padding:24px;">
  <h1 style="font-size:14px; letter-spacing:0.08em; color:#9a9a9a; margin-bottom:4px;">${heading}</h1>
  <p style="font-size:12px; color:#6b6b6b; margin-top:0;">Hi ${displayName || "there"} — ${bounds.start} to ${bounds.end}</p>

  <div style="font-size:40px; font-weight:700; margin:16px 0 0;">${fmtKm(totalDistance)} KM</div>
  <div style="font-size:13px; color:#9a9a9a;">${current.activityCount} ACTIVITIES · ${fmtHM(totalTime)} ACTIVE</div>

  <table style="width:100%; margin-top:24px; border-collapse:collapse;">
    <tr><td style="padding:8px 0; border-top:1px solid #262626;"><strong>WALKING</strong></td></tr>
    <tr><td style="color:#c9c9c9;">${current.steps.toLocaleString()} steps · ${fmtKm(current.walkingDistance)} km</td></tr>
    <tr><td style="padding:8px 0; border-top:1px solid #262626;"><strong>CYCLING</strong></td></tr>
    <tr><td style="color:#c9c9c9;">${fmtKm(current.cyclingDistance)} km · ${fmtHM(current.cyclingSeconds)}</td></tr>
    <tr><td style="padding:8px 0; border-top:1px solid #262626;"><strong>MOTORCYCLING</strong></td></tr>
    <tr><td style="color:#c9c9c9;">${fmtKm(current.motorcyclingDistance)} km · ${fmtHM(current.motorcyclingSeconds)}</td></tr>
  </table>

  <div style="margin-top:24px; padding-top:12px; border-top:1px solid #262626;">
    <strong style="font-size:12px; letter-spacing:0.06em; color:#9a9a9a;">VS PREVIOUS PERIOD</strong>
    <table style="width:100%; margin-top:8px;">
      <tr><td>Walking</td><td style="text-align:right;">${pctChange(current.walkingDistance, previous.walkingDistance)}</td></tr>
      <tr><td>Cycling</td><td style="text-align:right;">${pctChange(current.cyclingDistance, previous.cyclingDistance)}</td></tr>
      <tr><td>Motorcycling</td><td style="text-align:right;">${pctChange(current.motorcyclingDistance, previous.motorcyclingDistance)}</td></tr>
    </table>
  </div>

  <p style="margin-top:32px; font-size:11px; color:#5a5a5a;">PersonalStrava — a private, single-user activity tracker. Deterministic report, no AI-generated content.</p>
</body>
</html>`;
}

Deno.serve(async (req) => {
  try {
    const { period } = (await req.json()) as { period: Period };
    if (period !== "weekly" && period !== "monthly") {
      return new Response(JSON.stringify({ error: "period must be 'weekly' or 'monthly'" }), { status: 400 });
    }

    const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
    const bounds = computeBounds(period);
    const enabledColumn = period === "weekly" ? "weekly_report_enabled" : "monthly_report_enabled";

    const { data: profiles, error: profilesError } = await supabase
      .from("profiles")
      .select("id, display_name, report_email")
      .eq(enabledColumn, true);

    if (profilesError) throw profilesError;

    const results: Array<{ user_id: string; sent: boolean; error?: string }> = [];

    for (const profile of profiles ?? []) {
      try {
        const [{ data: currentRows, error: e1 }, { data: previousRows, error: e2 }, { data: authUser }] = await Promise.all([
          supabase
            .from("daily_stats")
            .select("*")
            .eq("user_id", profile.id)
            .gte("date", bounds.start)
            .lte("date", bounds.end),
          supabase
            .from("daily_stats")
            .select("*")
            .eq("user_id", profile.id)
            .gte("date", bounds.prevStart)
            .lte("date", bounds.prevEnd),
          supabase.auth.admin.getUserById(profile.id),
        ]);
        if (e1) throw e1;
        if (e2) throw e2;

        const current = sumStats((currentRows ?? []) as DailyStatsRow[]);
        const previous = sumStats((previousRows ?? []) as DailyStatsRow[]);

        // Skip silently if nothing happened this period — no empty reports.
        if (current.activityCount === 0 && current.steps === 0) {
          results.push({ user_id: profile.id, sent: false });
          continue;
        }

        const toEmail = profile.report_email || authUser?.user?.email;
        if (!toEmail) {
          results.push({ user_id: profile.id, sent: false, error: "no email on file" });
          continue;
        }

        const html = renderEmailHtml(profile.display_name ?? "", period, bounds, current, previous);

        const resendResp = await fetch("https://api.resend.com/emails", {
          method: "POST",
          headers: {
            Authorization: `Bearer ${RESEND_API_KEY}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            from: RESEND_FROM,
            to: [toEmail],
            subject: `Your ${bounds.label} movement report`,
            html,
          }),
        });

        if (!resendResp.ok) {
          throw new Error(`Resend API error: ${resendResp.status} ${await resendResp.text()}`);
        }

        results.push({ user_id: profile.id, sent: true });
      } catch (err) {
        results.push({ user_id: profile.id, sent: false, error: String(err) });
      }
    }

    return new Response(JSON.stringify({ period, bounds, results }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 });
  }
});
