package com.paladin173.microbes;

import android.graphics.Color;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

/** GPU renderer following the original Microbes point-sprite visual language. */
public final class MicrobesWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() { return new MicrobesEngine(); }

    private final class MicrobesEngine extends Engine {
        private final Object lock = new Object();
        private final MicrobeWorld world = new MicrobeWorld();
        private RenderThread renderThread;
        private boolean visible;
        private boolean surfaceReady;
        private int width;
        private int height;
        private float xOffset = 0.5f;

        @Override public void onVisibilityChanged(boolean visible) {
            synchronized (lock) {
                this.visible = visible;
                if (visible) startRendererLocked(); else stopRendererLocked();
            }
        }

        @Override public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            synchronized (lock) { surfaceReady = true; startRendererLocked(); }
        }

        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            synchronized (lock) {
                this.width = width; this.height = height;
                if (renderThread != null) renderThread.setSize(width, height);
            }
        }

        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            synchronized (lock) { surfaceReady = false; stopRendererLocked(); }
            super.onSurfaceDestroyed(holder);
        }

        @Override public void onOffsetsChanged(float xOffset, float yOffset, float xStep, float yStep,
                                               int xPixels, int yPixels) {
            synchronized (lock) {
                this.xOffset = xOffset;
                if (renderThread != null) renderThread.setOffset(xOffset);
            }
        }

        @Override public void onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                float nx = width == 0 ? 0.5f : event.getX() / width;
                float ny = height == 0 ? 0.5f : event.getY() / height;
                world.feed(nx, ny);
            }
            super.onTouchEvent(event);
        }

        private void startRendererLocked() {
            if (visible && surfaceReady && renderThread == null) {
                renderThread = new RenderThread(getSurfaceHolder(), world);
                renderThread.setSize(width, height);
                renderThread.setOffset(xOffset);
                renderThread.start();
            }
        }

        private void stopRendererLocked() {
            if (renderThread != null) { renderThread.requestStop(); renderThread = null; }
        }
    }

    private static final class RenderThread extends Thread {
        private static final String VERTEX_SHADER =
                "attribute vec4 aPosition;attribute vec4 aColor;uniform float uScroll;" +
                "varying vec4 vColor;varying vec2 vTransform;void main(){" +
                "gl_Position=vec4(aPosition.x+uScroll,aPosition.y,0.0,1.0);" +
                "gl_PointSize=aPosition.w;vColor=aColor;" +
                "vTransform=vec2(cos(aPosition.z),sin(aPosition.z));}";
        private static final String FRAGMENT_SHADER =
                "precision mediump float;varying vec4 vColor;varying vec2 vTransform;" +
                "void main(){vec2 p=gl_PointCoord.xy-.5;vec2 q=vec2(" +
                "p.x*vTransform.x-p.y*vTransform.y,p.x*vTransform.y+p.y*vTransform.x);" +
                "float d=length(q)*2.0;float ring=exp(-pow((d-.58)*7.0,2.0));" +
                "float halo=exp(-d*d*3.2);gl_FragColor=vec4(vColor.rgb,(ring*.9+halo*.22)*vColor.a);}";
        private final SurfaceHolder holder;
        private final MicrobeWorld world;
        private volatile boolean running = true;
        private volatile int width;
        private volatile int height;
        private volatile float scroll;

        RenderThread(SurfaceHolder holder, MicrobeWorld world) {
            super("Microbes-GpuRenderer"); this.holder = holder; this.world = world;
        }
        void setSize(int width, int height) { this.width = width; this.height = height; }
        void setOffset(float offset) { scroll = (offset - .5f) * -.24f; }
        void requestStop() { running = false; interrupt(); }

        @Override public void run() {
            EGLDisplay display = EGL14.EGL_NO_DISPLAY;
            EGLContext context = EGL14.EGL_NO_CONTEXT;
            EGLSurface surface = EGL14.EGL_NO_SURFACE;
            int program = 0;
            try {
                display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
                if (display == EGL14.EGL_NO_DISPLAY) return;
                int[] version = new int[2];
                if (!EGL14.eglInitialize(display, version, 0, version, 1)) return;
                EGLConfig config = chooseConfig(display);
                context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                        new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
                surface = EGL14.eglCreateWindowSurface(display, config, holder,
                        new int[]{EGL14.EGL_NONE}, 0);
                if (context == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE) return;
                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return;
                program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
                if (program == 0) return;
                int position = GLES20.glGetAttribLocation(program, "aPosition");
                int color = GLES20.glGetAttribLocation(program, "aColor");
                int scrollLocation = GLES20.glGetUniformLocation(program, "uScroll");
                GLES20.glUseProgram(program);
                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
                long last = SystemClock.uptimeMillis();
                while (running) {
                    long now = SystemClock.uptimeMillis();
                    float dt = Math.min(.05f, (now - last) / 1000f); last = now;
                    world.update(dt);
                    FloatBuffer positions = world.positions(width, height);
                    FloatBuffer colors = world.colors();
                    GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
                    GLES20.glClearColor(0f, 0f, .012f, 1f);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    GLES20.glUniform1f(scrollLocation, scroll);
                    GLES20.glEnableVertexAttribArray(position);
                    GLES20.glEnableVertexAttribArray(color);
                    GLES20.glVertexAttribPointer(position, 4, GLES20.GL_FLOAT, false, 0, positions);
                    GLES20.glVertexAttribPointer(color, 4, GLES20.GL_FLOAT, false, 0, colors);
                    GLES20.glDrawArrays(GLES20.GL_POINTS, 0, world.count());
                    GLES20.glDisableVertexAttribArray(position);
                    GLES20.glDisableVertexAttribArray(color);
                    EGL14.eglSwapBuffers(display, surface);
                    SystemClock.sleep(12L);
                }
            } finally {
                if (program != 0) GLES20.glDeleteProgram(program);
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
                    EGL14.eglTerminate(display);
                }
            }
        }

        private static EGLConfig chooseConfig(EGLDisplay display) {
            int[] count = new int[1];
            int[] attrs = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE};
            EGLConfig[] configs = new EGLConfig[1];
            EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0);
            return configs[0];
        }

        private static int createProgram(String vertex, String fragment) {
            int vs = compile(GLES20.GL_VERTEX_SHADER, vertex), fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment);
            if (vs == 0 || fs == 0) return 0;
            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs); GLES20.glLinkProgram(program);
            int[] linked = new int[1]; GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs);
            return linked[0] == GLES20.GL_TRUE ? program : 0;
        }

        private static int compile(int type, String source) {
            int shader = GLES20.glCreateShader(type); GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader);
            int[] compiled = new int[1]; GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            return compiled[0] == GLES20.GL_TRUE ? shader : 0;
        }
    }

    private static final class MicrobeWorld {
        private static final int MAX = 72;
        private final Random random = new Random(0x4D4943524FL);
        private final Microbe[] microbes = new Microbe[MAX];
        private final float[] positionData = new float[MAX * 4];
        private final float[] colorData = new float[MAX * 4];
        private final FloatBuffer positions = allocate(positionData.length);
        private final FloatBuffer colors = allocate(colorData.length);
        private float touchX = -10f, touchY = -10f, touchTime;
        private int count = MAX;

        MicrobeWorld() { for (int i = 0; i < MAX; i++) microbes[i] = new Microbe(random); }
        synchronized void feed(float x, float y) {
            touchX = x; touchY = y; touchTime = 1.5f;
            for (int i = 0; i < count; i++) {
                float dx = microbes[i].x - x, dy = microbes[i].y - y;
                if (dx * dx + dy * dy < .16f) microbes[i].energy = Math.min(1.4f, microbes[i].energy + .55f);
            }
        }
        synchronized void update(float dt) {
            touchTime = Math.max(0f, touchTime - dt);
            for (int i = 0; i < count; i++) {
                Microbe m = microbes[i]; m.update(dt, touchX, touchY, touchTime);
                if (m.energy > 1.15f && count < MAX) { m.energy *= .52f; microbes[count++] = new Microbe(m, random); }
            }
        }
        synchronized int count() { return count; }
        synchronized FloatBuffer positions(int width, int height) {
            float aspect = height == 0 ? 1f : (float) width / height;
            for (int i = 0; i < count; i++) {
                Microbe m = microbes[i]; positionData[i * 4] = (m.x * 2f - 1f) * Math.min(1.4f, Math.max(.75f, aspect));
                positionData[i * 4 + 1] = m.y * 2f - 1f; positionData[i * 4 + 2] = m.angle; positionData[i * 4 + 3] = m.size * (height / 700f);
            }
            positions.position(0); positions.put(positionData, 0, count * 4).position(0); return positions;
        }
        synchronized FloatBuffer colors() {
            for (int i = 0; i < count; i++) { Microbe m = microbes[i]; colorData[i * 4] = Color.red(m.color) / 255f;
                colorData[i * 4 + 1] = Color.green(m.color) / 255f; colorData[i * 4 + 2] = Color.blue(m.color) / 255f; colorData[i * 4 + 3] = .9f; }
            colors.position(0); colors.put(colorData, 0, count * 4).position(0); return colors;
        }
        private static FloatBuffer allocate(int length) { return ByteBuffer.allocateDirect(length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(); }
    }

    private static final class Microbe {
        private static final int[] PALETTE = {Color.rgb(255, 70, 70), Color.rgb(255, 205, 45), Color.rgb(40, 220, 135), Color.rgb(55, 125, 255)};
        float x, y, vx, vy, angle, size, energy; int color;
        Microbe(Random random) { x = random.nextFloat(); y = random.nextFloat(); vx = (random.nextFloat() - .5f) * .08f; vy = (random.nextFloat() - .5f) * .08f;
            angle = random.nextFloat() * 6.28f; size = 8f + random.nextFloat() * 13f; energy = .35f + random.nextFloat() * .45f; color = PALETTE[random.nextInt(PALETTE.length)]; }
        Microbe(Microbe parent, Random random) { this(random); x = parent.x + (random.nextFloat() - .5f) * .04f; y = parent.y + (random.nextFloat() - .5f) * .04f; size = parent.size * .62f; color = parent.color; }
        void update(float dt, float tx, float ty, float touchStrength) {
            vx += (.5f - x) * dt * .012f; vy += (.5f - y) * dt * .012f;
            if (touchStrength > 0f) { float rx = x - tx, ry = y - ty, d2 = Math.max(.002f, rx * rx + ry * ry); float force = touchStrength * dt * .012f / d2; vx += rx * force; vy += ry * force; }
            vx *= Math.pow(.985, dt * 60f); vy *= Math.pow(.985, dt * 60f); x += vx * dt * 8f; y += vy * dt * 8f; angle += dt * (1.2f + vx * 8f);
            energy = Math.max(.05f, energy - dt * .018f); size += (7f + energy * 16f - size) * dt * .22f;
            if (x < -.08f) x = 1.08f; if (x > 1.08f) x = -.08f; if (y < -.08f) y = 1.08f; if (y > 1.08f) y = -.08f;
        }
    }
}
