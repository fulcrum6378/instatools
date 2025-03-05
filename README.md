# InstaTools

InstaTools is a project containing applications which help people retrieve contents from
[Instagram](https://www.instagram.com/). Using these applications you can:

1. Download anyone's posts, reels, stories and highlights
2. Manage your saved posts

This project is written in pure Kotlin and contains these modules:

1. [**CORE**](core): containing the core tools for interacting with Instagram's private API.
2. [**ANDROID**](android): a full-fledged Android application for
3. [**CLI**](cli): an interactive command-line interface deployed as a JAR.
4. ~~JAVAFX~~: A GUI desktop application powered by JavaFX. (!not yet developed!)

## Android Application

### [Login.kt](android/kotlin/ir/mahdiparastesh/instatools/Login.kt)

This app lets its users log in to their Instagram account using a WebView.
As soon as the authentication process is done,
the cookies and plus some HTTP headers and configuration data extracted from HTML are stored
in a JSON file in the /files/ directory inside the internal storage.

That JSON file is an array of
[Account](android/kotlin/ir/mahdiparastesh/instatools/data/Account.kt)
objects and stores the data mentioned before.
These data are used later for dealing with Instagram's private API.
This app supports multiple accounts; each account has its own app data and shared preferences.
That's why the JSON file mentioned above is an array of multiple Account instances.

### [Main.kt](android/kotlin/ir/mahdiparastesh/instatools/Main.kt)

This activity contains 3 fragments:

1. **Favourites** ([PageFav.kt](android/kotlin/ir/mahdiparastesh/instatools/frag/PageFav.kt)) :
   Displays a local list of favourite Instagram profiles
   (its data is only stored locally not online on Instagram).
   Its theme is the yellow-brown one called Theme.InstaTools.Primary.
2. **Saved Posts** [PageSvd.kt](android/kotlin/ir/mahdiparastesh/instatools/frag/PageSvd.kt) :
   lists the saved posts and lets the users unsave and/or download them.
   [Downloads.kt](android/kotlin/ir/mahdiparastesh/instatools/Downloads.kt)
   can download any Instagram content, including post, story, reels, TV and profile picture.
   Their theme is the pink one called Theme.InstaTools.Secondary.
   [Downloader.kt](android/kotlin/ir/mahdiparastesh/instatools/job/Downloader.kt)
   is the service implemented mainly by Downloads (or it can run from anywhere).
   It queues the download items and downloads them one by one.
3.

### [Viewer.kt](android/kotlin/ir/mahdiparastesh/instatools/Viewer.kt)

This activity VIEWs any Instagram profile,
it uses the pink theme of Downloads and has 3 fragments like Main.kt:

1. [PageSto](android/kotlin/ir/mahdiparastesh/instatools/frag/PageSto.kt)
   shows their main story on top and then their highlighted stories.
2. [PageVwr](android/kotlin/ir/mahdiparastesh/instatools/frag/PageVwr.kt)
   shows their profile (which can be downloaded) picture and their posts.
3. [PageTag](android/kotlin/ir/mahdiparastesh/instatools/frag/PageTag.kt)
   shows their tagged posts.

### [Settings.kt](android/kotlin/ir/mahdiparastesh/instatools/Settings.kt)

There are different shared preference files related to each account
and there is also a global shared preference.
This activity controls both global settings (`gsp`)
and also settings of the current account (`sp)`.

### Subpackages

- [**api**](android/kotlin/ir/mahdiparastesh/instatools/api) : everything related to API,
  including back-end data models, endpoint addresses and their related utilities.
- [**data**](android/kotlin/ir/mahdiparastesh/instatools/data) : data models
- [**frag**](android/kotlin/ir/mahdiparastesh/instatools/frag) : all Fragments.
- [**job**](android/kotlin/ir/mahdiparastesh/instatools/job) : long-running tasks
- [**list**](android/kotlin/ir/mahdiparastesh/instatools/list) : all RecyclerView adapters
- [**util**](android/kotlin/ir/mahdiparastesh/instatools/util) : UX-related utilities
- [**view**](android/kotlin/ir/mahdiparastesh/instatools/view) : UI-related utilities

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

### Removed Features

1. InstaTools could track users' unfollowers in the past but this feature was removed
   because of Instagram's hypersensitivity.
2. InstaTools could export direct messages in TXT, PDF and HTML but this feature was removed
   because Instagram moved away from the old DM API and switched to WebSockets.

## Command-Line Interface

This application requires you to manually extract Instagram cookies from your browser
and put them in `cookies.txt` right beside the JAR or anywhere else but you'll have to specify.
Now, in order to:

1. To download bulk content from Instagram in desired qualities (using the parameter `--quality=?`):
    - `d`|`download`: direct links to posts or reels
    - `s`|`saved`: saved posts (+the ability to unsave them)
    - `p`|`posts`: posts of a profile
    - `t`|`tagged`: tagged posts of a profile
    - `r`|`story`: story of a profile
    - `h`|`highlight`: highlights of a profile
2. To retrieve information about a user, including their high-quality profile picture: `u`|`user`
3. To list users which have stories in your feed: `y`|`tray`

### License

```
Copyright © Mahdi Parastesh - All Rights Reserved.
```
