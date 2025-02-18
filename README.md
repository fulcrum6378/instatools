# InstaTools

An Android app which provides a set of useful tools for [Instagram](https://www.instagram.com/)
which it, deliberately or not, lacks!!

### [Login.kt](app/src/main/kotlin/ir/mahdiparastesh/instatools/Login.kt)

This app lets its users log in to their Instagram account using a WebView.
As soon as the authentication process is done,
the cookies and plus some HTTP headers and configuration data extracted from HTML are stored
in a JSON file in the /files/ directory inside the internal storage.

That JSON file is an array of
[Account](app/src/main/kotlin/ir/mahdiparastesh/instatools/data/Account.kt)
objects and stores the data mentioned before.
These data are used later for dealing with the web API of Instagram.
This app supports multiple accounts; each account has its own database and shared preferences.
That's why the JSON file mentioned above is an array of multiple Account instances.

### [Main.kt](app/src/main/kotlin/ir/mahdiparastesh/instatools/Main.kt)

InstaTools uses the Instagram Web API to performs 3 primary goals,
plus a few other miscellaneous things that will be mentioned later.

1. **Unfollowers** ([PageUnf.kt](app/src/main/kotlin/ir/mahdiparastesh/instatools/frag/PageUnf.kt)):
   Fetches a list of followers and then a list of the following,
   then sorts out the unfollowers and notifies when encounters new items.
   Its theme is the yellow-brown one called Theme.InstaTools.Primary.
   The fragment just shows the unfollowers from database, fetches are on its inner class
   [Inquiry](app/src/main/kotlin/ir/mahdiparastesh/instatools/frag/PageUnf.kt#L158).
2. **Download** : [PageSvd.kt](app/src/main/kotlin/ir/mahdiparastesh/instatools/frag/PageSvd.kt)
   lists the saved posts and lets the users unsave and/or download them.
   [Downloads.kt](app/src/main/kotlin/ir/mahdiparastesh/instatools/Downloads.kt)
   can download any Instagram content, including post, story, reels, TV and profile picture.
   Their theme is the pink one called Theme.InstaTools.Secondary.
   [Downloader.kt](app/src/main/kotlin/ir/mahdiparastesh/instatools/job/Downloader.kt)
   is the service implemented mainly by Downloads (or it can run from anywhere).
   It queues the download items and downloads them one by one.
3. **DM Export** ([PageBox.kt](app/src/main/kotlin/ir/mahdiparastesh/instatools/frag/PageBox.kt)):
   Fetches all messages in a DM conversation(thread) and optionally downloads the media inside
   those messages and exports them all into HTML, PDF or TXT file types.
   Its theme is the blue one called Theme.InstaTools.Tertiary.
   The fragment shows conversations and messages, and the export work is on
   [Exporter.kt](app/src/main/kotlin/ir/mahdiparastesh/instatools/job/Exporter.kt)

### [Viewer.kt](app/src/main/kotlin/ir/mahdiparastesh/instatools/Viewer.kt)

This activity VIEWs any Instagram profile,
it uses the pink theme of Downloads and has 3 fragments like Main.kt:

1. [PageSto](app/src/main/kotlin/ir/mahdiparastesh/instatools/frag/PageSto.kt)
   shows their main story on top and then their highlighted stories.
2. [PageVwr](app/src/main/kotlin/ir/mahdiparastesh/instatools/frag/PageVwr.kt)
   shows their profile (which can be downloaded) picture and their posts.
3. [PageTag](app/src/main/kotlin/ir/mahdiparastesh/instatools/frag/PageTag.kt)
   shows their tagged posts.

### [Favourites.kt](app/src/main/kotlin/ir/mahdiparastesh/instatools/Favourites.kt)

This activity shows merely locally favourited Instagram profiles
(data is only stored in the local database not by Instagram).
These profiles sink in the *Unfollowers* page and are easily accessible.

### [Settings.kt](app/src/main/kotlin/ir/mahdiparastesh/instatools/Settings.kt)

There are different shared preference files related to each account
and there is also a global shared preference.
This activity controls both global settings (gsp) and also settings of the current account (sp).

### Subpackages

- [**api**](app/src/main/kotlin/ir/mahdiparastesh/instatools/api) : everything related to API,
  including back-end data models, endpoint addresses and their related utilities.
- [**data**](app/src/main/kotlin/ir/mahdiparastesh/instatools/data) :
  data models used for storing in databases or other local files.
- [**expt**](app/src/main/kotlin/ir/mahdiparastesh/instatools/expt) :
  utilities that help exporting direct messages.
- [**frag**](app/src/main/kotlin/ir/mahdiparastesh/instatools/frag) : all Fragments.
- [**job**](app/src/main/kotlin/ir/mahdiparastesh/instatools/job) : long-running tasks
- [**list**](app/src/main/kotlin/ir/mahdiparastesh/instatools/list) : all RecyclerView adapters
- [**util**](app/src/main/kotlin/ir/mahdiparastesh/instatools/util) : UX-related utilities
- [**view**](app/src/main/kotlin/ir/mahdiparastesh/instatools/view) : UI-related utilities

### Localisation

It currently supports these languages:

- English (en-GB)
- Persian (fa)

### Unlucky Publishing Story

I started this project of course for a commercial purpose,
I mean I wanted to earn money using Google AdMob, but alas...
due to the fu-king [U.S. sanctions on Iran](https://www.state.gov/iran-sanctions/) I failed to do so!!

This app also got suspended in Google Play for 2 times for *"copyright infringement"*;
first because of using the word "Insta" in InstaTools and its icon being similar to Instagram,
then I changed the icon and the name to "Downloader for Instagram" and also wrote a legal disclaimer,
and published it with an app ID suffix *.beth*,
it was fu-king suspended again because of using "for Instagram" in the app title.
So I was disappointed. Similar things happened in Galaxy Store for perhaps tens of times
with those as-hole reviewers!

I continued the app in the 2 Iranian app stores { Bazaar & Myket }.

### License

```
Copyright © Mahdi Parastesh - All Rights Reserved.
```
