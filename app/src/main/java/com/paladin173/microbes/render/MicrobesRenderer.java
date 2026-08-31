package com.paladin173.microbes.render;

import android.opengl.GLES20;
import android.util.Log;

import com.paladin173.microbes.simulation.MicrobeWorld;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

final class MicrobesRenderer {
    private static final String TAG = "MicrobesRenderer";
    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;"
                    + "attribute vec4 aColor;"
                    + "uniform float uScroll;"
                    + "varying vec3 vColor;"
                    + "varying vec2 vTransform;"
                    + "varying float vWidthScale;"
                    + "void main(){"
                    + "gl_Position=vec4(aPosition.x+uScroll,aPosition.y,0.0,1.0);"
                    + "gl_PointSize=aPosition.w;"
                    + "vColor=aColor.rgb;"
                    + "vTransform=vec2(cos(aPosition.z),sin(aPosition.z));"
                    + "vWidthScale=1.0/mix(0.5,0.9,aColor.a);"
                    + "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;"
                    + "varying vec3 vColor;"
                    + "varying vec2 vTransform;"
                    + "varying float vWidthScale;"
                    + "void main(){"
                    + "vec2 p=gl_PointCoord.xy-0.5;"
                    + "vec2 local=vec2("
                    + "p.x*vTransform.x+p.y*vTransform.y,"
                    + "-p.x*vTransform.y+p.y*vTransform.x);"
                    + "float forward=smoothstep(-0.15,0.5,local.x);"
                    + "float taper=mix(1.0,1.22,forward);"
                    + "float h=length(local*vec2(1.0,vWidthScale*taper))*2.0;"
                    + "float shell=clamp(-pow(h-0.4,2.0)*30.0+0.5,0.0,0.5);"
                    + "float body=clamp(-length(local*vec2("
                    + "1.0,((vWidthScale-1.0)*0.2+1.0)*taper)*2.0)+1.0,0.0,0.5);"
                    + "gl_FragColor=vec4(vColor,shell+body);"
                    + "}";
    private static final String FOOD_VERTEX_SHADER =
            "attribute vec3 aPosition;"
                    + "uniform float uTime;"
                    + "uniform float uSizeScale;"
                    + "uniform float uScroll;"
                    + "void main(){"
                    + "gl_Position=vec4(aPosition.x+uScroll,aPosition.y,0.0,1.0);"
                    + "float pulse=cos(uTime*2.0+aPosition.z*10.0)*0.2+0.7;"
                    + "gl_PointSize=20.0*pulse*uSizeScale;"
                    + "}";
    private static final String FOOD_FRAGMENT_SHADER =
            "precision mediump float;"
                    + "void main(){"
                    + "float d=length(gl_PointCoord.xy-.5)*2.0;"
                    + "float alpha=exp(-d*d*4.0);"
                    + "gl_FragColor=vec4(0.75,0.85,1.0,alpha);"
                    + "}";
    private static final String CORPSE_VERTEX_SHADER =
            "attribute vec4 aPosition;"
                    + "uniform float uScroll;"
                    + "varying vec2 vTransform;"
                    + "void main(){"
                    + "gl_Position=vec4(aPosition.x+uScroll,aPosition.y,0.0,1.0);"
                    + "gl_PointSize=aPosition.w;"
                    + "vTransform=vec2(cos(aPosition.z),sin(aPosition.z));"
                    + "}";
    private static final String CORPSE_FRAGMENT_SHADER =
            "precision mediump float;"
                    + "varying vec2 vTransform;"
                    + "void main(){"
                    + "vec2 p=gl_PointCoord.xy-.5;"
                    + "vec2 q=vec2("
                    + "p.x*vTransform.x+p.y*vTransform.y,"
                    + "-p.x*vTransform.y+p.y*vTransform.x);"
                    + "float d=length(q*vec2(1.0,1.35))*2.0;"
                    + "float shell=exp(-pow((d-.55)*8.0,2.0));"
                    + "gl_FragColor=vec4(0.55,0.6,0.7,shell*.24);"
                    + "}";
    private static final String DECORATION_VERTEX_SHADER =
            "attribute vec3 aPosition;"
                    + "uniform float uTime;"
                    + "uniform float uSizeScale;"
                    + "uniform float uScroll;"
                    + "varying vec4 vColor;"
                    + "void main(){"
                    + "vec2 drift=vec2(sin((uTime+aPosition.x)*.1),"
                    + "cos((uTime+aPosition.y)*.1))*mix(.025,.09,aPosition.z);"
                    + "vec2 position=(aPosition.xy+drift)*mix(.2,.9,aPosition.z);"
                    + "gl_Position=vec4(position.x+uScroll,position.y,0.0,1.0);"
                    + "gl_PointSize=200.0*mix(.5,1.0,aPosition.z)*uSizeScale;"
                    + "vColor=vec4(.6,.6,1.0,mix(.03,.08,aPosition.z));"
                    + "}";
    private static final String DECORATION_FRAGMENT_SHADER =
            "precision mediump float;"
                    + "varying vec4 vColor;"
                    + "void main(){"
                    + "float d=length(gl_PointCoord.xy-.5)*2.0;"
                    + "gl_FragColor=vec4(vColor.rgb,vColor.a*exp(-d*d*4.0));"
                    + "}";

    private final MicrobeWorld world;
    private final FloatBuffer positions = allocate(MicrobeWorld.MAX_COUNT * 4);
    private final FloatBuffer colors = allocate(MicrobeWorld.MAX_COUNT * 4);
    private final FloatBuffer foodPositions = allocate(MicrobeWorld.FOOD_CAPACITY * 3);
    private final FloatBuffer corpsePositions = allocate(MicrobeWorld.CORPSE_CAPACITY * 4);
    private final FloatBuffer decorationPositions =
            allocate(MicrobeWorld.DECORATION_CAPACITY * 3);
    private int program;
    private int foodProgram;
    private int corpseProgram;
    private int decorationProgram;
    private int positionLocation;
    private int colorLocation;
    private int scrollLocation;
    private int foodPositionLocation;
    private int foodTimeLocation;
    private int foodSizeLocation;
    private int foodScrollLocation;
    private int corpsePositionLocation;
    private int corpseScrollLocation;
    private int decorationPositionLocation;
    private int decorationTimeLocation;
    private int decorationSizeLocation;
    private int decorationScrollLocation;
    private float elapsedSeconds;
    private boolean loggedFirstFrame;

    MicrobesRenderer(MicrobeWorld world) {
        this.world = world;
    }

    void create() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        foodProgram = createProgram(FOOD_VERTEX_SHADER, FOOD_FRAGMENT_SHADER);
        corpseProgram = createProgram(CORPSE_VERTEX_SHADER, CORPSE_FRAGMENT_SHADER);
        decorationProgram = createProgram(
                DECORATION_VERTEX_SHADER,
                DECORATION_FRAGMENT_SHADER
        );
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition");
        colorLocation = GLES20.glGetAttribLocation(program, "aColor");
        scrollLocation = GLES20.glGetUniformLocation(program, "uScroll");
        foodPositionLocation = GLES20.glGetAttribLocation(foodProgram, "aPosition");
        foodTimeLocation = GLES20.glGetUniformLocation(foodProgram, "uTime");
        foodSizeLocation = GLES20.glGetUniformLocation(foodProgram, "uSizeScale");
        foodScrollLocation = GLES20.glGetUniformLocation(foodProgram, "uScroll");
        corpsePositionLocation = GLES20.glGetAttribLocation(corpseProgram, "aPosition");
        corpseScrollLocation = GLES20.glGetUniformLocation(corpseProgram, "uScroll");
        decorationPositionLocation =
                GLES20.glGetAttribLocation(decorationProgram, "aPosition");
        decorationTimeLocation = GLES20.glGetUniformLocation(decorationProgram, "uTime");
        decorationSizeLocation =
                GLES20.glGetUniformLocation(decorationProgram, "uSizeScale");
        decorationScrollLocation =
                GLES20.glGetUniformLocation(decorationProgram, "uScroll");
        GLES20.glUseProgram(program);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        float[] pointSizeRange = new float[2];
        GLES20.glGetFloatv(GLES20.GL_ALIASED_POINT_SIZE_RANGE, pointSizeRange, 0);
        Log.i(
                TAG,
                "GL ready: renderer=" + GLES20.glGetString(GLES20.GL_RENDERER)
                        + ", version=" + GLES20.glGetString(GLES20.GL_VERSION)
                        + ", pointSize=" + pointSizeRange[0] + ".." + pointSizeRange[1]
                        + ", attributes=" + positionLocation + "/" + colorLocation
        );
        checkGlError("renderer initialization");
    }

    void draw(int width, int height, float offset, float deltaSeconds) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        elapsedSeconds += deltaSeconds;
        world.setViewport(safeWidth, safeHeight);
        world.update(deltaSeconds);
        int decorationCount = world.writeDecorationRenderData(decorationPositions);
        int corpseCount = world.writeCorpseRenderData(corpsePositions, safeHeight);
        int foodCount = world.writeFoodRenderData(foodPositions);
        int count = world.writeMicrobeRenderData(positions, colors, safeHeight);
        float scroll = (offset - 0.5f) * -0.24f;

        GLES20.glViewport(0, 0, safeWidth, safeHeight);
        GLES20.glClearColor(0f, 0f, 0.012f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(decorationProgram);
        GLES20.glUniform1f(decorationTimeLocation, elapsedSeconds);
        GLES20.glUniform1f(decorationSizeLocation, Math.min(safeHeight / 800f, 1.3f));
        GLES20.glUniform1f(decorationScrollLocation, scroll);
        GLES20.glEnableVertexAttribArray(decorationPositionLocation);
        GLES20.glVertexAttribPointer(
                decorationPositionLocation,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                decorationPositions
        );
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, decorationCount);
        GLES20.glDisableVertexAttribArray(decorationPositionLocation);

        GLES20.glUseProgram(corpseProgram);
        GLES20.glUniform1f(corpseScrollLocation, scroll);
        GLES20.glEnableVertexAttribArray(corpsePositionLocation);
        GLES20.glVertexAttribPointer(
                corpsePositionLocation, 4, GLES20.GL_FLOAT, false, 0, corpsePositions
        );
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, corpseCount);
        GLES20.glDisableVertexAttribArray(corpsePositionLocation);

        GLES20.glUseProgram(foodProgram);
        GLES20.glUniform1f(foodTimeLocation, elapsedSeconds);
        GLES20.glUniform1f(foodSizeLocation, safeHeight / 800f);
        GLES20.glUniform1f(foodScrollLocation, scroll);
        GLES20.glEnableVertexAttribArray(foodPositionLocation);
        GLES20.glVertexAttribPointer(
                foodPositionLocation, 3, GLES20.GL_FLOAT, false, 0, foodPositions
        );
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, foodCount);
        GLES20.glDisableVertexAttribArray(foodPositionLocation);

        GLES20.glUseProgram(program);
        GLES20.glUniform1f(scrollLocation, scroll);
        GLES20.glEnableVertexAttribArray(positionLocation);
        GLES20.glEnableVertexAttribArray(colorLocation);
        GLES20.glVertexAttribPointer(
                positionLocation, 4, GLES20.GL_FLOAT, false, 0, positions
        );
        GLES20.glVertexAttribPointer(
                colorLocation, 4, GLES20.GL_FLOAT, false, 0, colors
        );
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count);
        checkGlError("frame draw");
        if (!loggedFirstFrame && width > 1 && height > 1) {
            loggedFirstFrame = true;
            Log.i(
                    TAG,
                    "First frame: viewport=" + width + "x" + height
                            + ", count=" + count
                            + ", firstPosition=" + positions.get(0) + "," + positions.get(1)
                            + ", firstSize=" + positions.get(3)
            );
        }
        GLES20.glDisableVertexAttribArray(positionLocation);
        GLES20.glDisableVertexAttribArray(colorLocation);
    }

    void release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        if (foodProgram != 0) {
            GLES20.glDeleteProgram(foodProgram);
            foodProgram = 0;
        }
        if (corpseProgram != 0) {
            GLES20.glDeleteProgram(corpseProgram);
            corpseProgram = 0;
        }
        if (decorationProgram != 0) {
            GLES20.glDeleteProgram(decorationProgram);
            decorationProgram = 0;
        }
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compile(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int result = GLES20.glCreateProgram();
        GLES20.glAttachShader(result, vertexShader);
        GLES20.glAttachShader(result, fragmentShader);
        GLES20.glLinkProgram(result);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, linked, 0);
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        if (linked[0] != GLES20.GL_TRUE) {
            String log = GLES20.glGetProgramInfoLog(result);
            GLES20.glDeleteProgram(result);
            throw new IllegalStateException("Unable to link GL program: " + log);
        }
        return result;
    }

    private static int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] != GLES20.GL_TRUE) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Unable to compile GL shader: " + log);
        }
        return shader;
    }

    private static void checkGlError(String operation) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            throw new IllegalStateException(
                    operation + " failed with GL error 0x" + Integer.toHexString(error)
            );
        }
    }

    private static FloatBuffer allocate(int floatCount) {
        return ByteBuffer.allocateDirect(floatCount * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
    }
}
