/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.actions.file

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.roomData.tooltips.Tooltip
import io.github.rosemoe.sora.widget.CodeEditor

class ShowTooltipAction(private val context: Context, override val order: Int) :
    EditorRelatedAction() {
    override val id: String = "ide.editor.code.text.format"
    override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS

    init {
        label = context.getString(R.string.title_show_tooltip)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_docs)
    }

    override suspend fun execAction(data: ActionData): Any {
        val editor = data.getEditor()!!
        val cursor = editor.text.cursor
        val activity = data.getActivity()
        val word = editor.text.substring(cursor.left, cursor.right)
        if (cursor.isSelected) {
            activity?.getTooltipData(word)?.let { tooltipData ->
                showTooltip(
                    editor,
                    tooltipData,
                    activity
                )
            }
        }

        return true
    }

    private fun showTooltip(
        editor: CodeEditor,
        tooltip: Tooltip,
        activity: EditorHandlerActivity,
        block: () -> Unit = { activity.openFAQActivity(tooltip.descriptionFull) }
    ) {
        val inflater = LayoutInflater.from(context)
        val tooltipView = inflater.inflate(R.layout.layout_tooltip, null)

        val informationFirstLevel: TextView =
            tooltipView.findViewById(R.id.tooltip_inforamtion_first_level)
        val showMoreSecondLevelButton: Button =
            tooltipView.findViewById(R.id.btn_show_more_second_level)
        val showMoreThirdLevelButton: Button =
            tooltipView.findViewById(R.id.btn_show_more_third_levewl)
        val informationSecondLevel: TextView =
            tooltipView.findViewById(R.id.tooltip_information_second_level)

        informationFirstLevel.text = tooltip.descriptionShort

        informationSecondLevel.text = tooltip.descriptionLong

        showMoreSecondLevelButton.setOnClickListener {
            informationSecondLevel.isVisible = true
            showMoreThirdLevelButton.isVisible = true
            showMoreSecondLevelButton.isVisible = false
        }

        val popupWindow = PopupWindow(
            tooltipView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true

        showMoreThirdLevelButton.setOnClickListener {
            block.invoke()
            popupWindow.dismiss()
        }

        popupWindow.showAtLocation(editor, Gravity.CENTER, 0, 0)
    }
}
