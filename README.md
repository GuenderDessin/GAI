# GTok

GTok is a NetBeans Java project that retrieves the TikTok trending feed,
converts the response into relational rows, calculates engagement rates,
and can insert results into SQL Server.

## Requirements

- JDK 21 or newer
- Apache NetBeans (the project was created with NetBeans 28)
- A ScrapeCreators API key

The required Jackson libraries are included in `libs/`, so a newly cloned
copy can be opened and built without manually downloading those JARs.

## API-key setup

The API key is intentionally not stored in this repository. Set it separately
on every computer.

Windows PowerShell:

```powershell
[Environment]::SetEnvironmentVariable(
    "SCRAPE_CREATORS_API_KEY",
    "replace-with-your-key",
    "User"
)
```

Restart NetBeans after setting the variable so the IDE inherits it.

## Build and run

Open the project directory in NetBeans and use **Clean and Build**. The main
class is `gtok.GTok`, which opens the Trending Studio Swing dashboard. Select a proxy region and click **Fetch trending feed**, then select a row and click **Watch video here**. Each click makes one API request; no request runs automatically. The dashboard removes duplicate video IDs, shows credits, and supports sorting by clicking column headers. The API key comes from your environment and is never shown in the window.

To run the console feed collector directly, run
`gtok.Trending`.

From a command prompt with Ant available:

```text
ant clean jar
```

## Working from multiple computers

1. Pull before starting work.


## Embedded video browser (Windows x64)

`VideoBrowserFrame` is a movable, resizable, maximizable `JInternalFrame`
inside the dashboard. It uses Microsoft Edge WebView2 through the bundled
Swing WebView library, so the page and HTML5 video use the same browser engine.

Use a Windows x64 JDK 21 or newer and install the
[Microsoft Edge WebView2 Evergreen Runtime](https://developer.microsoft.com/microsoft-edge/webview2/)
if it is not already present. The Java bridge (`ca.weblite:webview:1.10.1`)
and its native library are included in `libs/`.

After **Clean and Build**, use **Run Project (F6)**. Select a video and click
**Watch video here**, then click Play inside the player. Video player,
Original post, and Reload stay inside the dashboard. Selecting another video
reuses the internal window; closing it releases the browser. Minimizing
releases the native browser and stops playback; restoring reloads the page.

Outside NetBeans, run `run-dashboard.bat` after building. Keep `dist/lib`
with `dist/GTok.jar`. The launcher and NetBeans settings enable native access
for the embedded browser. JavaFX is no longer required or loaded.

Playback uses TikTok's official `/player/v1/{post_id}` URL with autoplay off.
TikTok may require consent/login or decline a deleted, private, restricted,
or unavailable post. **Original post** opens the post in the same window.
No ScrapeCreators API key is sent to the browser, and watching or reloading a
video does not fetch another feed. Browser cookies/site storage are managed
by the local WebView2 runtime.

Dependencies and references:
- [Swing WebView](https://github.com/webliteca/swingwebview) (MIT; see `libs/webview-LICENSE.txt`)
- [Pinned Maven Central artifact](https://repo.maven.apache.org/maven2/ca/weblite/webview/1.10.1/)
- [WebView2 Runtime](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/distribution)
- [TikTok player](https://developers.tiktok.com/doc/embed-player)
