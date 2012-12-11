/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;

import java.util.Arrays;
import java.util.List;

/**
 * Checks for missing {@code recycle} calls on resources that encourage it
 */
public class RecycleDetector extends Detector implements ClassScanner {
    /** Problems with missing recycle calls */
    public static final Issue ISSUE = Issue.create(
        "Recycle", //$NON-NLS-1$
        "Looks for missing recycle() calls on resources",

        "Many resources, such as TypedArrays, VelocityTrackers, etc., " +
        "should be recycled (with a `recycle()` call) after use. This lint check looks " +
        "for missing `recycle()` calls.",

        Category.PERFORMANCE,
        7,
        Severity.WARNING,
        RecycleDetector.class,
        Scope.CLASS_FILE_SCOPE);

    // Target method names
    private static final String RECYCLE = "recycle";                                  //$NON-NLS-1$
    private static final String OBTAIN = "obtain";                                    //$NON-NLS-1$
    private static final String OBTAIN_NO_HISTORY = "obtainNoHistory";                //$NON-NLS-1$
    private static final String OBTAIN_MESSAGE = "obtainMessage";                     //$NON-NLS-1$
    private static final String OBTAIN_ATTRIBUTES = "obtainAttributes";               //$NON-NLS-1$
    private static final String OBTAIN_TYPED_ARRAY = "obtainTypedArray";              //$NON-NLS-1$
    private static final String OBTAIN_STYLED_ATTRIBUTES = "obtainStyledAttributes";  //$NON-NLS-1$

    // Target owners
    private static final String VELOCITY_TRACKER_CLS = "android/view/VelocityTracker";//$NON-NLS-1$
    private static final String TYPED_ARRAY_CLS = "android/content/res/TypedArray";   //$NON-NLS-1$
    private static final String CONTEXT_CLS = "android/content/Context";              //$NON-NLS-1$
    private static final String MOTION_EVENT_CLS = "android/view/MotionEvent";        //$NON-NLS-1$
    private static final String MESSAGE_CLS = "android/os/Message";                   //$NON-NLS-1$
    private static final String HANDLER_CLS = "android/os/Handler";                   //$NON-NLS-1$
    private static final String RESOURCES_CLS = "android/content/res/Resources";      //$NON-NLS-1$
    private static final String PARCEL_CLS = "android/os/Parcel";                     //$NON-NLS-1$

    // Target description signatures
    private static final String TYPED_ARRAY_SIG = "Landroid/content/res/TypedArray;"; //$NON-NLS-1$
    private static final String MESSAGE_SIG = "Landroid/os/Message;";                 //$NON-NLS-1$

    private boolean mObtainsTypedArray;
    private boolean mRecyclesTypedArray;
    private boolean mObtainsTracker;
    private boolean mRecyclesTracker;
    private boolean mObtainsMessage;
    private boolean mRecyclesMessage;
    private boolean mObtainsMotionEvent;
    private boolean mRecyclesMotionEvent;
    private boolean mObtainsParcel;
    private boolean mRecyclesParcel;

    /** Constructs a new {@link RecycleDetector} */
    public RecycleDetector() {
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        int phase = context.getDriver().getPhase();
        if (phase == 1) {
            if (mObtainsTypedArray && !mRecyclesTypedArray
                    || mObtainsTracker && !mRecyclesTracker
                    || mObtainsMessage && !mRecyclesMessage
                    || mObtainsParcel && !mRecyclesParcel
                    || mObtainsMotionEvent && !mRecyclesMotionEvent) {
                context.getDriver().requestRepeat(this, Scope.CLASS_FILE_SCOPE);
            }
        }
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableCallNames() {
        return Arrays.asList(
                RECYCLE,
                OBTAIN_STYLED_ATTRIBUTES,
                OBTAIN,
                OBTAIN_ATTRIBUTES,
                OBTAIN_TYPED_ARRAY,
                OBTAIN_MESSAGE,
                OBTAIN_NO_HISTORY
        );
    }

    @Override
    public void checkCall(
            @NonNull ClassContext context,
            @NonNull ClassNode classNode,
            @NonNull MethodNode method,
            @NonNull MethodInsnNode call) {
        String name = call.name;
        String owner = call.owner;
        String desc = call.desc;
        int phase = context.getDriver().getPhase();
        if (RECYCLE.equals(name) && desc.equals("()V")) { //$NON-NLS-1$
            if (owner.equals(TYPED_ARRAY_CLS)) {
                mRecyclesTypedArray = true;
            } else if (owner.equals(VELOCITY_TRACKER_CLS)) {
                mRecyclesTracker = true;
            } else if (owner.equals(MESSAGE_CLS)) {
                mRecyclesMessage = true;
            } else if (owner.equals(MOTION_EVENT_CLS)) {
                mRecyclesMotionEvent = true;
            } else if (owner.equals(PARCEL_CLS)) {
                mRecyclesParcel = true;
            }
        } else if (owner.equals(MOTION_EVENT_CLS)) {
            if (OBTAIN.equals(name) || OBTAIN_NO_HISTORY.equals(name)) {
                mObtainsMotionEvent = true;
                if (phase == 2 && !mRecyclesMotionEvent) {
                    context.report(ISSUE, method, call, context.getLocation(call),
                            getErrorMessage(MOTION_EVENT_CLS),
                            null);
                } else if (phase == 1
                        && checkMethodFlow(context, classNode, method, call, MOTION_EVENT_CLS)) {
                    // Already reported error above; don't do global check
                    mRecyclesMotionEvent = true;
                }
            }
        } else if (OBTAIN_MESSAGE.equals(name)) {
            if (owner.equals(HANDLER_CLS) && desc.endsWith(MESSAGE_SIG)) {
                mObtainsMessage = true;
                if (phase == 2 && !mRecyclesMessage) {
                    context.report(ISSUE, method, call, context.getLocation(call),
                            getErrorMessage(MESSAGE_CLS), null);
                }
            }
        } else if (OBTAIN.equals(name)) {
            if (owner.equals(VELOCITY_TRACKER_CLS)) {
                mObtainsTracker = true;
                if (phase == 2 && !mRecyclesTracker) {
                    context.report(ISSUE, method, call, context.getLocation(call),
                            getErrorMessage(VELOCITY_TRACKER_CLS),
                            null);
                }
            } else if (owner.equals(MESSAGE_CLS) && desc.endsWith(MESSAGE_SIG)) {
                // TODO: Handle Message constructor?
                mObtainsMessage = true;
                if (phase == 2 && !mRecyclesMessage) {
                    context.report(ISSUE, method, call, context.getLocation(call),
                            getErrorMessage(MESSAGE_CLS),
                            null);
                }
            } else if (owner.equals(PARCEL_CLS)) {
                mObtainsParcel = true;
                if (phase == 2 && !mRecyclesParcel) {
                    context.report(ISSUE, method, call, context.getLocation(call),
                            getErrorMessage(PARCEL_CLS),
                            null);
                } else if (phase == 1
                        && checkMethodFlow(context, classNode, method, call, PARCEL_CLS)) {
                    // Already reported error above; don't do global check
                    mRecyclesParcel = true;
                }
            }
        } else if (OBTAIN_STYLED_ATTRIBUTES.equals(name)
                || OBTAIN_ATTRIBUTES.equals(name)
                || OBTAIN_TYPED_ARRAY.equals(name)) {
            if ((owner.equals(CONTEXT_CLS) || owner.equals(RESOURCES_CLS))
                    && desc.endsWith(TYPED_ARRAY_SIG)) {
                mObtainsTypedArray = true;
                if (phase == 2 && !mRecyclesTypedArray) {
                    context.report(ISSUE, method, call, context.getLocation(call),
                            getErrorMessage(TYPED_ARRAY_CLS),
                            null);
                } else if (phase == 1
                        && checkMethodFlow(context, classNode, method, call, TYPED_ARRAY_CLS)) {
                    // Already reported error above; don't do global check
                    mRecyclesTypedArray = true;
                }
            }
        }
    }

    /** Computes an error message for a missing recycle of the given type */
    private static String getErrorMessage(String owner) {
        String className = owner.substring(owner.lastIndexOf('/') + 1);
        return String.format("This %1$s should be recycled after use with #recycle()",
                className);
    }

    /**
     * Ensures that the given allocate call in the given method has a
     * corresponding recycle method, also within the same method, OR, the
     * allocated resource flows out of the method (either as a return value, or
     * into a field, or into some other method (with some known exceptions; e.g.
     * passing a MotionEvent into another MotionEvent's constructor is fine)
     * <p>
     * Returns true if an error was found
     */
    private static boolean checkMethodFlow(ClassContext context, ClassNode classNode,
            MethodNode method, MethodInsnNode call, String recycleOwner) {
        RecycleTracker interpreter = new RecycleTracker(context, method, call, recycleOwner);
        ResourceAnalyzer analyzer = new ResourceAnalyzer(interpreter);
        interpreter.setAnalyzer(analyzer);
        try {
            analyzer.analyze(classNode.name, method);
            if (!interpreter.isRecycled() && !interpreter.isEscaped()) {
                Location location = context.getLocation(call);
                String message = getErrorMessage(recycleOwner);
                context.report(ISSUE, method, call, location, message, null);
                return true;
            }
        } catch (AnalyzerException e) {
            context.log(e, null);
        }

        return false;
    }

    /**
     * ASM interpreter which tracks the instances of the allocated resource, and
     * checks whether it is eventually passed to a {@code recycle()} call. If the
     * value flows out of the method (to a field, or a method call), it will
     * also consider the resource recycled.
     */
    private static class RecycleTracker extends Interpreter {
        private final Value INSTANCE = BasicValue.INT_VALUE; // Only identity matters, not value
        private final Value RECYCLED = BasicValue.FLOAT_VALUE;
        private final Value UNKNOWN = BasicValue.UNINITIALIZED_VALUE;

        private final ClassContext mContext;
        private final MethodNode mMethod;
        private final MethodInsnNode mObtainNode;
        private boolean mIsRecycled;
        private boolean mEscapes;
        private final String mRecycleOwner;
        private ResourceAnalyzer mAnalyzer;

        public RecycleTracker(
                @NonNull ClassContext context,
                @NonNull MethodNode method,
                @NonNull MethodInsnNode obtainNode,
                @NonNull String recycleOwner) {
            super(Opcodes.ASM4);
            mContext = context;
            mMethod = method;
            mObtainNode = obtainNode;
            mRecycleOwner = recycleOwner;
        }

        /**
         * Sets the analyzer associated with the interpreter, such that it can
         * get access to the execution frames
         */
        void setAnalyzer(ResourceAnalyzer analyzer) {
            mAnalyzer = analyzer;
        }

        /**
         * Returns whether a recycle call was found for the given method
         *
         * @return true if the resource was recycled
         */
        public boolean isRecycled() {
            return mIsRecycled;
        }

        /**
         * Returns whether the target resource escapes from the method, for
         * example as a return value, or a field assignment, or getting passed
         * to another method
         *
         * @return true if the resource escapes
         */
        public boolean isEscaped() {
            return mEscapes;
        }

        @Override
        public Value newOperation(AbstractInsnNode node) throws AnalyzerException {
            return UNKNOWN;
        }

        @Override
        public Value newValue(final Type type) {
            if (type != null && type.getSort() == Type.VOID) {
                return null;
            } else {
                return UNKNOWN;
            }
        }

        @Override
        public Value copyOperation(AbstractInsnNode node, Value value) throws AnalyzerException {
            return value;
        }

        @Override
        public Value binaryOperation(AbstractInsnNode node, Value value1, Value value2)
                throws AnalyzerException {
            if (node.getOpcode() == Opcodes.PUTFIELD) {
                if (value2 == INSTANCE) {
                    mEscapes = true;
                }
            }
            return merge(value1, value2);
        }

        @Override
        public Value naryOperation(AbstractInsnNode node, List values) throws AnalyzerException {
            if (node == mObtainNode) {
                return INSTANCE;
            }

            MethodInsnNode call = null;
            if (node.getType() == AbstractInsnNode.METHOD_INSN) {
                call = (MethodInsnNode) node;
                if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    if (call.name.equals(RECYCLE) && call.owner.equals(mRecycleOwner)) {
                        if (values != null && values.size() == 1 && values.get(0) == INSTANCE) {
                            mIsRecycled = true;
                            Frame frame = mAnalyzer.getCurrentFrame();
                            if (frame != null) {
                                int localSize = frame.getLocals();
                                for (int i = 0; i < localSize; i++) {
                                    Value local = frame.getLocal(i);
                                    if (local == INSTANCE) {
                                        frame.setLocal(i, RECYCLED);
                                    }
                                }
                                int stackSize = frame.getStackSize();
                                if (stackSize == 1 && frame.getStack(0) == INSTANCE) {
                                    frame.pop();
                                    frame.push(RECYCLED);
                                }
                            }
                            return RECYCLED;
                        }
                    }
                }
            }

            if (values != null && values.size() >= 1) {
                // Skip the first element: method calls *on* the TypedArray are okay
                int start = node.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1;
                for (int i = 0, n = values.size(); i < n; i++) {
                    Object v = values.get(i);
                    if (v == INSTANCE && i >= start) {
                        // Known special cases
                        if (node.getOpcode() == Opcodes.INVOKESTATIC) {
                            assert call != null;
                            if (call.name.equals(OBTAIN) &&
                                    call.owner.equals(MOTION_EVENT_CLS)) {
                                return UNKNOWN;
                            }
                        }

                        // Passing the instance to another method: could leak
                        // the instance out of this method (for example calling
                        // a method which recycles it on our behalf, or store it
                        // in some holder which will recycle it later). In this
                        // case, just assume that things are okay.
                        mEscapes = true;
                    } else if (v == RECYCLED && call != null) {
                        Location location = mContext.getLocation(call);
                        String message = String.format("This %1$s has already been recycled",
                                mRecycleOwner.substring(mRecycleOwner.lastIndexOf('/') + 1));
                        mContext.report(ISSUE, mMethod, call, location, message, null);
                    }
                }
            }

            return UNKNOWN;
        }

        @Override
        public Value unaryOperation(AbstractInsnNode node, Value value) throws AnalyzerException {
            return value;
        }

        @Override
        public Value ternaryOperation(AbstractInsnNode node, Value value1, Value value2,
                Value value3) throws AnalyzerException {
            if (value1 == RECYCLED || value2 == RECYCLED || value3 == RECYCLED) {
                return RECYCLED;
            } else  if (value1 == INSTANCE || value2 == INSTANCE || value3 == INSTANCE) {
                return INSTANCE;
            }
            return UNKNOWN;
        }

        @Override
        public void returnOperation(AbstractInsnNode node, Value value1, Value value2)
                throws AnalyzerException {
            if (value1 == INSTANCE || value2 == INSTANCE) {
                mEscapes = true;
            }
        }

        @Override
        public Value merge(Value value1, Value value2) {
            if (value1 == RECYCLED || value2 == RECYCLED) {
                return RECYCLED;
            } else if (value1 == INSTANCE || value2 == INSTANCE) {
                return INSTANCE;
            }
            return UNKNOWN;
        }
    }

    private static class ResourceAnalyzer extends Analyzer {
        private Frame mCurrent;
        private Frame mFrame1;
        private Frame mFrame2;

        public ResourceAnalyzer(Interpreter interpreter) {
            super(interpreter);
        }

        Frame getCurrentFrame() {
            return mCurrent;
        }

        @Override
        protected void init(String owner, MethodNode m) throws AnalyzerException {
            mCurrent = mFrame2;
            super.init(owner, m);
        }

        @Override
        protected Frame newFrame(int nLocals, int nStack) {
            // Stash the two most recent frame allocations. When init is called the second
            // most recently seen frame is the current frame used during execution, which
            // is where we need to replace INSTANCE with RECYCLED when the void
            // recycle method is called.
            Frame newFrame = super.newFrame(nLocals, nStack);
            mFrame2 = mFrame1;
            mFrame1 = newFrame;
            return newFrame;
        }
    }
}
