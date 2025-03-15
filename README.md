# InstaTools

InstaTools is a project containing applications which help you retrieve contents from
[Instagram](https://www.instagram.com/). Using these applications you can:

1. Download anyone's posts, reels, stories and highlights
2. Manage your saved posts
3. Monitor your feed stories tray

This project is written in pure Kotlin and contains these modules:

1. [**CORE**](core): containing the core utilities.
2. [**ANDROID**](android): a full-fledged Android application.
3. [**CLI**](cli): an interactive command-line interface deployed as a JAR
   or as an EXE via [GraalVM](https://www.graalvm.org/).
4. ~~JAVAFX~~: A GUI desktop application powered by JavaFX. (!cancelled!)

### Subpackages

- [**api**](android/kotlin/ir/mahdiparastesh/instatools/api) : everything related to API,
  including back-end data models, endpoint addresses and their related utilities.
- [**data**](android/kotlin/ir/mahdiparastesh/instatools/data) : data models
- [**frag**](android/kotlin/ir/mahdiparastesh/instatools/frag) : all Fragments.
- [**job**](android/kotlin/ir/mahdiparastesh/instatools/job) : long-running tasks
- [**list**](android/kotlin/ir/mahdiparastesh/instatools/list) : all RecyclerView adapters
- [**util**](android/kotlin/ir/mahdiparastesh/instatools/util) : UX-related utilities
- [**view**](android/kotlin/ir/mahdiparastesh/instatools/view) : UI-related utilities

## Android Application

#### [InstaTools.kt](android/kotlin/ir/mahdiparastesh/instatools/InstaTools.kt)

Subclass of *Application*, controls data repositories and different Instagram accounts a user possesses.

#### [Login.kt](android/kotlin/ir/mahdiparastesh/instatools/Login.kt)

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

#### [Main.kt](android/kotlin/ir/mahdiparastesh/instatools/Main.kt)

This activity contains 3 fragments:

1. **Favourites** ([PageFav.kt](android/kotlin/ir/mahdiparastesh/instatools/frag/PageFav.kt)) :
   Displays a local list of favourite Instagram profiles
   (its data is only stored locally not online on Instagram).
   Its theme is the yellow-brown one: `Theme.InstaTools.Primary`.
2. **Saved Posts** ([PageSvd.kt](android/kotlin/ir/mahdiparastesh/instatools/frag/PageSvd.kt)) :
   lists the saved posts and lets the users unsave and/or download them.
   Its theme is the pink-purple one: `Theme.InstaTools.Secondary`.
3. **Reels Tray** ([PageTry.kt](android/kotlin/ir/mahdiparastesh/instatools/frag/PageTry.kt)) :
   lists stories from your feed.
   Its theme is the blue one called: `Theme.InstaTools.Tertiary`.

#### [Viewer.kt](android/kotlin/ir/mahdiparastesh/instatools/Viewer.kt)

This activity displays any Instagram profile,
it uses the pink-purple theme of *PageSvd* and has 3 fragments like Main.kt:

1. [PageSto](android/kotlin/ir/mahdiparastesh/instatools/frag/PageSto.kt)
   shows their main story on top and then their highlighted stories.
2. [PageVwr](android/kotlin/ir/mahdiparastesh/instatools/frag/PageVwr.kt)
   shows their profile (which can be downloaded) picture and their posts.
3. [PageTag](android/kotlin/ir/mahdiparastesh/instatools/frag/PageTag.kt)
   shows their tagged posts.

#### [Downloads.kt](android/kotlin/ir/mahdiparastesh/instatools/Downloads.kt)

This activity can download IG posts and reels via their links and can receive link shares from Instagram.
it uses the pink-purple theme of *PageSvd*.
Also it shows any ongoing download and it can directly control
[DownloadService](android/kotlin/ir/mahdiparastesh/instatools/job/DownloadService.kt),
which is an Android Service that queues [Download](core/kotlin/ir/mahdiparastesh/instatools/data/Download.kt)
items and downloads them one by one.

#### [Settings.kt](android/kotlin/ir/mahdiparastesh/instatools/Settings.kt)

There are different shared preference files related to each account
and there is also a global shared preference.
This activity controls both global settings (`gsp`)
and also settings of the current account (`sp)`.
it uses the blue theme of *PageTry*.

### Localisation

This app only supports these languages:

- English (en-GB)
- Persian (fa)

### The Unlucky Publishing Story

- Google Play removed this app 2 times because of *"copyright infringement"*;
  they said I had stolen Instagram's trademark!!!
  That the icon is too similar to that of Instagram and the name *"Insta"* belongs to Instagram.

- Galaxy Store removed this app tens of times because I just used the word "Instagram" inside my app!

- Myket didn't allow me to republish it because it was against the laws of IRI!

- [Bazaar](https://cafebazaar.ir/app/ir.mahdiparastesh.instatools) still accepts updates
  but most of its users aren't interested in such apps!!

### Removed Features

- Following numerous IG accounts slowly and one by one; this feature was removed
  because of Instagram's hypersensitivity.
- Analysing users' unfollowers; this feature was removed
  because of Instagram's hypersensitivity.
- Exporting direct messages in HTML, PDF and TXT; this feature was removed
  because Instagram moved away from the old DM API and switched to encrypted WebSockets.

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
