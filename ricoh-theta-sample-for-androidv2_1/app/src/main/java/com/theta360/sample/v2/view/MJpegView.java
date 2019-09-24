package com.theta360.sample.v2.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * Motion JPEG view
 */
public class MJpegView extends SurfaceView implements SurfaceHolder.Callback {
    private MJpegViewThread mMJpegViewThread = null;
    private MJpegInputStream mMJpegInputStream = null;
    private boolean existSurface = false;
    private int mDisplayWidth;
    private int mDisplayHeight;

    /**
     * Constructor
     * @param context
     */
    public MJpegView(Context context) {
        super(context);
        init();
    }

    /**
     * Constructor
     * @param context
     * @param attrs
     */
    public MJpegView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Constructor
     * @param context
     * @param attrs
     * @param defStyleAttr
     */
    public MJpegView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Initialization process
     */
    private void init() {
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        mDisplayWidth = getWidth();
        mDisplayHeight = getHeight();
    }

    /**
     * Start playback
     */
    public void play() {
        if (mMJpegViewThread != null) {
            stopPlay();
        }

        if(mMJpegInputStream != null) {
            if (mMJpegViewThread != null) {
                if (mMJpegViewThread.getState() == Thread.State.NEW) {
                    mMJpegViewThread.start();
                }
            } else {
                mMJpegViewThread = new MJpegViewThread(getHolder());
                mMJpegViewThread.start();
            }
        }
    }

    /**
     * Stop playback
     */
    public void stopPlay() {
        if (mMJpegViewThread != null) {
            mMJpegViewThread.cancel();
            boolean retry = true;
            while (retry) {
                try {
                    mMJpegViewThread.join();
                    retry = false;
                    mMJpegViewThread = null;
                } catch (InterruptedException e) {
                    e.getStackTrace();
                }
            }
        }
    }

    /**
     * Set source stream for receiving motion JPEG
     * @param source Source stream
     */
    public void setSource(MJpegInputStream source) {
        mMJpegInputStream = source;
        play();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        existSurface = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        synchronized(holder) {
            mDisplayWidth = width;
            mDisplayHeight = height;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        existSurface = false;
        stopPlay();
    }

    /**
     * Thread class for receiving motion JPEG
     */
    private class MJpegViewThread extends Thread {
        private final SurfaceHolder mSurfaceHolder;
        private boolean keepRunning = true;

        /**
         * Constructor
         * @param surfaceHolder
         */
        public MJpegViewThread(SurfaceHolder surfaceHolder) {
            mSurfaceHolder = surfaceHolder;
        }

        /**
         * Acquire image size according to display area<p>
         *     Calculates the size that fits the display area while maintaining the aspect ratio of the motion JPEG.
         * @param bitmapWidth Width of motion JPEG
         * @param bitmapHeight Height of motion JPEG
         * @return Image size
         */
        private Rect getImageRect(int bitmapWidth, int bitmapHeight) {
            float bitmapAspectRatio = (float) bitmapWidth / (float) bitmapHeight;
            bitmapWidth = mDisplayWidth;
            bitmapHeight = (int) (mDisplayWidth / bitmapAspectRatio);
            if (bitmapHeight > mDisplayHeight) {
                bitmapHeight = mDisplayHeight;
                bitmapWidth = (int) (mDisplayHeight * bitmapAspectRatio);
            }
            int bitmapX = (mDisplayWidth / 2) - (bitmapWidth / 2);
            int bitmapY = (mDisplayHeight / 2) - (bitmapHeight / 2);
            return new Rect(0, bitmapY, mDisplayWidth, bitmapHeight + bitmapY);
        }

        /**
         * Abort thread
         */
        public void cancel() {
            keepRunning = false;
        }

        @Override
        public void run() {
            Bitmap bitmap;
            Rect bitmapRect;
            Canvas bitmapCanvas = null;

            while (keepRunning) {
                if (existSurface) {
                    try {
                        bitmapCanvas = mSurfaceHolder.lockCanvas();
                        synchronized (mSurfaceHolder) {
                            try {
                                if ((mMJpegInputStream != null) && (bitmapCanvas != null)) {
                                    bitmap = mMJpegInputStream.readMJpegFrame();
                                    bitmapRect = getImageRect(bitmap.getWidth(), bitmap.getHeight());
                                    bitmapCanvas.drawColor(Color.BLACK);
                                    bitmapCanvas.drawBitmap(bitmap, null, bitmapRect, new Paint());
                                    bitmap.recycle();
                                }
                            } catch (IOException e) {
                                e.getStackTrace();
                                keepRunning = false;
                            }
                        }
                    } finally {
                        if (bitmapCanvas != null) {
                            mSurfaceHolder.unlockCanvasAndPost(bitmapCanvas);
                        }
                    }
                }
            }

            bitmapCanvas = mSurfaceHolder.lockCanvas();
            synchronized (mSurfaceHolder) {
                if (bitmapCanvas != null) {
                    bitmapCanvas.drawColor(Color.BLACK);
                }
            }

            if (bitmapCanvas != null) {
                mSurfaceHolder.unlockCanvasAndPost(bitmapCanvas);
            }

            if (mMJpegInputStream != null) {
                try {
                    mMJpegInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
