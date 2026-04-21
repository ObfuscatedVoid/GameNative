package com.winlator.renderer;

public class ViewTransformation {
    public int viewOffsetX;
    public int viewOffsetY;
    public int viewWidth;
    public int viewHeight;
    public float scaleX;
    public float scaleY;
    public float sceneScaleX;
    public float sceneScaleY;
    public float sceneOffsetX;
    public float sceneOffsetY;

    public void update(int outerWidth, int outerHeight, int innerWidth, int innerHeight, DisplayMode mode) {
        float fitX = (float) outerWidth / innerWidth;
        float fitY = (float) outerHeight / innerHeight;

        if (mode == null) mode = DisplayMode.FIT;
        switch (mode) {
            case STRETCH:
                scaleX = fitX;
                scaleY = fitY;
                break;
            case ZOOM:
                scaleX = scaleY = Math.max(fitX, fitY);
                break;
            case FIT:
            default:
                scaleX = scaleY = Math.min(fitX, fitY);
                break;
        }

        viewWidth = (int) Math.ceil(innerWidth * scaleX);
        viewHeight = (int) Math.ceil(innerHeight * scaleY);
        viewOffsetX = (outerWidth - viewWidth) / 2;
        viewOffsetY = (outerHeight - viewHeight) / 2;

        sceneScaleX = (float) viewWidth / (float) outerWidth;
        sceneScaleY = (float) viewHeight / (float) outerHeight;
        sceneOffsetX = (innerWidth - innerWidth * sceneScaleX) * 0.5f;
        sceneOffsetY = (innerHeight - innerHeight * sceneScaleY) * 0.5f;
    }
}
