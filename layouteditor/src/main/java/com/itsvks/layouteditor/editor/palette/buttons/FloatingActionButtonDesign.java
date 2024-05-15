package com.itsvks.layouteditor.editor.palette.buttons;

import android.content.Context;
import android.graphics.Canvas;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.itsvks.layouteditor.utils.Constants;
import com.itsvks.layouteditor.utils.Utils;

public class FloatingActionButtonDesign extends FloatingActionButton {

  private boolean drawStrokeEnabled;
  private boolean isBlueprint;

  public FloatingActionButtonDesign(Context context) {
    super(context);
  }
  
  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);

    if (drawStrokeEnabled)
      Utils.drawDashPathStroke(
          this, canvas, isBlueprint ? Constants.BLUEPRINT_DASH_COLOR : Constants.DESIGN_DASH_COLOR);
  }

  public void setStrokeEnabled(boolean enabled) {
    drawStrokeEnabled = enabled;
    invalidate();
  }
  
  @Override
  public void draw(Canvas canvas) {
    if (isBlueprint) Utils.drawDashPathStroke(this, canvas, Constants.BLUEPRINT_DASH_COLOR);
    else super.draw(canvas);
  }

  public void setBlueprint(boolean isBlueprint) {
    this.isBlueprint = isBlueprint;
    invalidate();
  }
}
