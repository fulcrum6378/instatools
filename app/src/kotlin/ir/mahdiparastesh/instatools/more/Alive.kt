package ir.mahdiparastesh.instatools.more

/**
 * An abstract class, when implemented gives:
 * - active : a Boolean which indicates if the object is alive or not,
 *            and must be changed during onCreate and onDestroy.
 * In this app we implemented it on companion objects indirectly.
 *
 * @see BaseActivity.ActivityCompanion
 */
abstract class Alive {
    var active = false

    companion object {
        fun anyLiving() = BaseActivity.anyActive() || ForegroundService.anyRunning()
    }
}
