package com.whalemate.wechat

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** 下拉快捷开关：一键显示/隐藏悬浮球，不用打开 App。 */
class WhaleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "先给鲸聊悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }

        val settings = AppSettings(this)
        settings.showBall = !settings.showBall

        val service = ChatA11yService.instance
        if (service != null) {
            service.refreshBall()
        } else {
            Toast.makeText(this, "无障碍没开：开启后球会自动恢复", Toast.LENGTH_SHORT).show()
        }

        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val on = AppSettings(this).showBall && Settings.canDrawOverlays(this)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "鲸聊"
        tile.updateTile()
    }
}
