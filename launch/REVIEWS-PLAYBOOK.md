# Reviews playbook

Good reviews are earned, not bought. The rating in the first two weeks sets the
tone for everything after, and the single biggest lever is replying to every
review fast and well, especially the negative ones.

Copy rule: no em dashes, warm and human, never defensive.

---

## What is allowed and what is not

Allowed:
- Asking real users and testers to leave an honest review. No conditions.
- Replying to every review from the Play Console.
- A gentle in-app prompt to rate, shown after the user has actually used the app.

Not allowed, and worth avoiding entirely:
- Offering anything in return for a review or a rating.
- Asking for five stars specifically. Ask for an honest review.
- Review swaps or any coordinated rating scheme.

---

## The pre-warm email (send now, while still in closed testing)

Google does not let testers write a public Play review until the app has a
production release. Rather than asking for something they cannot do yet, send
this now to keep them engaged and collect quotable feedback in the meantime, so
they act fast the moment T-0 hits instead of having forgotten about the app.

Subject: A quick update, and one honest ask (2 minutes)

Hi everyone,

Thank you for testing Granular Volume this past week. Every day you stay opted in
moves the countdown to full public launch forward, and that matters more than you
might realize.

One thing came up that I want to explain honestly: right now, you won't see an
option to write a Play Store review. That's not a bug on your end, and it's not
something broken on ours either. Google only unlocks public reviews once an app
reaches full production release, and we're still about a week away from even
being eligible to apply. The review option will simply appear on its own once
that happens. Nothing to troubleshoot.

So instead of asking you to do something you can't do yet, here's what would
genuinely help right now:

1. Reply to this email with anything on your mind, good or rough. Even one line
   helps: what you use it for, what surprised you, anything that felt off.
2. If you're on Reddit, feel free to share your experience wherever feels
   natural. Real words from real testers carry weight.

The moment the app goes fully public, I'll send one short follow-up and ask if
you'd leave that review then. No pressure either way, today's ask is just honest
feedback.

Thank you for being part of this from the start. It means a lot.

## The launch ask (send to testers the moment it goes public, same day as T-0)

Subject: We're live! (and I owe you one)

Hi everyone,

Big news: Granular Volume just went public on Google Play. The 14 days you stayed
opted in are the actual reason it's there, so genuinely, thank you.

Here's the one favor I'll ask today. If the app has been useful to you, a quick
star rating and a line or two of honest review would help more than you'd think,
especially in these first few days. Doesn't need to be long, just what you use it
for and whether it does the job.

https://play.google.com/store/apps/details?id=granularvolume.com

(Opening that link on Android and scrolling to "Rate this app" takes about 20
seconds. If the app is already installed, tapping the link should jump straight
there.)

And if there's ever anything I can do to return the favor, testing something of
yours, giving feedback, a shoutout, whatever, just ask. You got me here, I'd love
to pay that forward.

Thank you again. Really.

---

## Response templates

Personalize each one. Reference the specific thing the reviewer said. Never paste
the same line twice in a row.

### 5 star, happy

Thank you, this means a lot. If there is anything you would want it to do next,
tell me and I will look into it.

### 4 star, mostly happy with a small gripe

Thanks for the kind words and for the honest note. [Address the specific gripe.]
I am keeping a list of small improvements and this is on it.

### 3 star, a feature request

Appreciate the feedback. [Name the requested feature.] is a fair ask and I have
added it to the list. If you would be open to it, tell me a bit more about how you
would use it so I build the right thing.

### 1 or 2 star, a bug

Sorry this did not work right for you. I want to fix it. Could you tell me your
device and Android version, and what happened. You can also open an issue at
https://github.com/Rzuss/granular-volume/issues and I will get on it quickly. Once
it is fixed I will follow up here.

### 1 or 2 star, a permission worry

I understand the concern, and thank you for raising it. The overlay permission is
only used to float the volume dial over other apps, and the app makes no network
requests and collects nothing. The whole source is public at
https://github.com/Rzuss/granular-volume if you want to check. Happy to answer
anything.

### 1 or 2 star, a misunderstanding of what the app does

Thanks for trying it. Granular Volume is for going quieter than the phone's lowest
step, not for making things louder. If quiet listening is what you were after and
it did not behave, tell me your device and I will help. If you needed a booster,
this is honestly not the right tool and I would rather you find one that fits.

---

## The recovery loop

1. A negative review arrives.
2. Reply within 24 hours with empathy and a concrete next step.
3. If it is a bug, fix it fast and ship.
4. Reply again on the same review to say it is fixed in the latest version and
   gently invite a re-rating. Many 1 star reviews become 5 star this way.

The point is not to argue a rating up. It is to show every future reader that the
developer listens and fixes things. That is what converts a browser into an install.
