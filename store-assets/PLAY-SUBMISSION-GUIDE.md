# Granular Volume — Google Play Submission Guide

כל מה שנדרש להעלאה מקצועית לחנות Google Play.
מסומן: [ME] = אני הכנתי | [YOU] = דורש פעולה שלך

---

## מה מוכן — טבלת נכסים

| נכס | קובץ | סטטוס |
|-----|------|--------|
| **AAB לחנות** | `GranularVolume-PlayStore.aab` (2.22 MB) | [ME] מוכן |
| **APK לטסטינג** | `GranularVolume-v1.1.0.apk` (2.55 MB) | [ME] מוכן |
| **אייקון חנות 512x512** | `store-assets/play_icon_512.png` | [ME] מוכן |
| **Feature Graphic 1024x500** | `store-assets/feature_graphic_1024x500.png` | [ME] מוכן |
| **5 צילומי מסך** | `store-assets/screenshot_1..5.png` | [ME] מוכן |
| **מדיניות פרטיות** | `store-assets/privacy-policy.html` | [ME] מוכן (צריך אחסון) |
| **טקסט ליסטינג** | `store-assets/STORE-LISTING.md` | [ME] מוכן |
| **מפתח חתימה** | `granularvolume-release.jks` | [ME] מוכן |
| **סיסמאות keystore** | `KEYSTORE-CREDENTIALS.txt` | [ME] מוכן |

> ⚠️ **גיבוי קריטי:** גבה את `granularvolume-release.jks` + `KEYSTORE-CREDENTIALS.txt` לענן פרטי / דיסק חיצוני עכשיו. אובדן ה-keystore = אי אפשר לפרסם עדכונים לעולם.

---

## שלב 1 — פתיחת חשבון מפתח [YOU]
**עלות:** $25 חד-פעמי לכל החיים

1. היכנס ל-https://play.google.com/console עם `rotemzus@gmail.com`
2. **"Create a developer account"** → סוג: **"Yourself"** (אדם פרטי)
3. שם מפתח ציבורי — יופיע מתחת לאפליקציה בחנות (בחר שם שמתאים לך)
4. שלם $25 בכרטיס אשראי
5. **אימות זהות:** תעודת זהות / דרכון + כתובת מגורים — חובה לחשבונות אישיים חדשים

⏱️ האימות לוקח שעות עד ימים. המתן לאישור במייל לפני שממשיכים.

---

## שלב 2 — אחסון מדיניות פרטיות [YOU]
**עלות:** חינם

Play דורש כתובת URL ציבורית. הקובץ `privacy-policy.html` מוכן.

### אפשרות מומלצת — GitHub Pages (חינם, 5 דקות):
1. פתח חשבון GitHub בחינם: https://github.com
2. **"New repository"** → שם: `granular-volume-privacy` → **Public** → Create
3. לחץ **"Add file" → "Upload files"**
4. העלה את `store-assets/privacy-policy.html` → שנה שם ל-`index.html` → Commit
5. **Settings → Pages → Branch: main → Save**
6. אחרי ~דקה: `https://YOUR-USERNAME.github.io/granular-volume-privacy/`

שמור את הכתובת הזו — תשתמש בה בשלב 4.

---

## שלב 3 — יצירת האפליקציה ב-Play Console [YOU]

1. Play Console → **"Create app"**
2. App name: `Granular Volume`
3. Default language: `English (United States)`
4. App or game: **App**
5. Free or paid: **Free**
6. סמן את שתי ההצהרות → **Create app**

---

## שלב 4 — Store listing (טקסט + גרפיקה) [YOU]

### Main store listing:
כל הטקסט מוכן ב-`STORE-LISTING.md`. העתק-הדבק:

| שדה | מה להעתיק |
|-----|-----------|
| App name | `Granular Volume` |
| Short description | ראה STORE-LISTING.md |
| Full description | ראה STORE-LISTING.md |
| App icon | העלה `play_icon_512.png` |
| Feature graphic | העלה `feature_graphic_1024x500.png` |
| Phone screenshots | העלה `screenshot_1.png` עד `screenshot_5.png` |

### App content (Dashboard tasks):
- **Privacy policy URL** → הדבק את הכתובת משלב 2
- **App access** → "All functionality is available without special access"
- **Ads** → **No, my app does not contain ads**
- **Content rating** → מלא שאלון → ענה **No** לכל → דירוג: **Everyone**
- **Target audience** → **13+** (general utility)
- **Data safety** → "No data collected" + "No data shared" (ראה STORE-LISTING.md לתשובות מדויקות)
- **Government apps** → No
- **Financial features** → None

---

## שלב 5 — העלאה ל-Closed Testing [YOU]

> ⚠️ **כלל 20 הבודקים:** חשבונות אישיים חדשים (אחרי נובמבר 2023) חייבים **≥20 בודקים פעילים במשך ≥14 ימים רצופים** בבדיקה סגורה לפני שאפשר לפרסם לכולם.

1. Play Console → **Testing → Closed testing → Create new release**
2. **Play App Signing:** קבל את ברירת המחדל (Google-managed key) — **חשוב: לחץ אישור!**
   > Google תחזיק את מפתח החתימה הסופי. ה-keystore שלך הוא "upload key" בלבד.
   > זה מה שמבטיח שמשתמשים לא יראו שום אזהרה — Play חותמת רשמית.
3. **Upload** → גרור `GranularVolume-PlayStore.aab`
4. Release name: `1.1.0` (אוטומטי) | Release notes → העתק מ-STORE-LISTING.md
5. **Save → Review release → Start rollout to Closed testing**

### גיוס 20 הבודקים:
- Closed testing → טאב **Testers** → Create email list → הוסף 20+ כתובות Gmail
- שתף את **opt-in link** שמופיע שם עם כל בודק
- כל בודק: לוחץ הלינק → "Become a tester" → מוריד מ-Play → מתקין → **משאיר מותקן**

🎯 **ספירת 14 הימים מתחילה רק כשיש 20 בודקים opted-in בו-זמנית.**

לגיוס בודקים: משפחה, חברים, עמיתים, קבוצות וואטסאפ — כל מי עם חשבון Gmail.

---

## שלב 6 — בקשת גישה לפרודקשן (אחרי 14 יום) [YOU]

1. Play Console יציג: **"Apply for production access"**
2. שאלון קצר — תאר את תהליך הבדיקה בכנות (20 בודקים, X ימים, תגובות חיוביות)
3. שלח. Google בודקת ידנית — **כמה ימים**
4. אחרי אישור: **Production → Create release → Upload same AAB → Rollout 100%**
5. אחרי אישור ביקורת סופית (24-48 שעות): **האפליקציה גלויה לכולם** 🎉

---

## למה לא יהיו אזהרות "תוכנה לא אמינה"

| מה קורה | מדוע |
|---------|-------|
| אזהרה ב-sideload | הקובץ מגיע ממקור לא מאומת |
| **אפס אזהרות מ-Play** | Google חותמת + Play Protect מאמת |

כשהאפליקציה עוברת דרך Play — Google חותמת אותה ב-Play App Signing, Play Protect מאמת, וכל הורדה מהחנות היא ממקור מהימן. בדיוק כמו כל אפליקציה אחרת.

---

## ציר זמן ריאלי

| שלב | זמן |
|-----|-----|
| פתיחת חשבון + אימות זהות | שעות–ימים |
| הקמת ליסטינג + העלאת AAB | ~שעה (הכל מוכן) |
| Closed testing — 20 בודקים × 14 יום | **14 ימים מינימום** |
| אישור גישה לפרודקשן | כמה ימים |
| ביקורת הפרסום הסופית | 24–48 שעות |
| **סה"כ מחשבון ריק עד גלוי בחנות** | **~3–4 שבועות** |

---

## צ'קליסט הגשה

- [ ] keystore מגובה במקום חיצוני
- [ ] חשבון מפתח נפתח ואומת ($25)
- [ ] מדיניות פרטיות מאוחסנת ויש לי URL ציבורי
- [ ] אפליקציה נוצרה ב-Play Console
- [ ] Store listing מלא (טקסט + אייקון + feature graphic + 5 screenshots)
- [ ] כל משימות Dashboard מולאו (data safety, content rating, target audience)
- [ ] AAB הועלה ל-Closed testing
- [ ] 20+ בודקים opted-in
- [ ] 14 ימים עברו
- [ ] בקשת production access נשלחה
- [ ] שוחרר ל-Production
- [ ] **האפליקציה חיה בחנות** 🎉
