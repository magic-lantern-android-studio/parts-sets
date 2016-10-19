package com.wizzer.mle.parts.sets;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.wizzer.mle.math.MlVector2;
import com.wizzer.mle.parts.j3d.roles.I3dRole;
import com.wizzer.mle.parts.j3d.sets.I3dSet;
import com.wizzer.mle.parts.stages.Mle3dStage;
import com.wizzer.mle.runtime.MleTitle;
import com.wizzer.mle.runtime.core.IMleCallbackId;
import com.wizzer.mle.runtime.core.IMleProp;
import com.wizzer.mle.runtime.core.IMleRole;
import com.wizzer.mle.runtime.core.MleRuntimeException;
import com.wizzer.mle.runtime.core.MleSet;
import com.wizzer.mle.runtime.core.MleSize;
import com.wizzer.mle.math.MlScalar;
import com.wizzer.mle.math.MlTransform;
import com.wizzer.mle.math.MlRotation;
import com.wizzer.mle.math.MlVector3;
import com.wizzer.mle.runtime.core.MleStage;
import com.wizzer.mle.runtime.event.MleEvent;
import com.wizzer.mle.runtime.event.MleEventCallback;
import com.wizzer.mle.runtime.event.MleEventManager;
import com.wizzer.mle.runtime.scheduler.MleTask;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Created by msm on 8/23/16.
 */
public class Mle3dSet extends MleSet implements I3dSet
{
    /** Render callback id (scheduler). */
    protected MleTask m_renderCBId = null;
    /** Resize callback id (event dispatcher). */
    protected IMleCallbackId m_resizeCBId = null;

    // The properties exposed in the DWP are "position" and "size".

    /**
     * Contains x and y position in screen coordinate space;
     * (0, 0) corresponds to the lower left corner of the window.
     */
    public MlVector2 position = new MlVector2();;

    /** Contains width and height in screen coordinate space. */
    public MlVector2 size = new MlVector2();

    /**
     * The the root of the role scene graph.
     */
    protected I3dRole m_root = null;

    /**
     * This inner class is used to process resize events.
     */
    protected class Mle3dSetResizeCallback extends MleEventCallback
    {
        /**
         * The default constructor.
         */
        public Mle3dSetResizeCallback()
        {
            super();
            // Do nothing extra.
        }

        /**
         * The callback dispatch method. This method is responsible for
         * handling the <i>resize</i> event.
         *
         * @param event The event that is being dispatched by the handler.
         * @param clientData Client data registered with this handler.
         *
         * @return If the event is successfully dispatched, then <b>true</b> should be
         * returned. Otherwise, <b>false</b> should be returned.
         *
         * @see com.wizzer.mle.runtime.event.IMleEventCallback#dispatch(com.wizzer.mle.runtime.event.MleEvent, java.lang.Object)
         */
        public boolean dispatch(MleEvent event, Object clientData)
        {
            boolean result = true;

            // Get the Set and Stage.
            Mle3dSet theSet = (Mle3dSet) clientData;
            Mle3dStage theStage = (Mle3dStage) Mle3dStage.getInstance();

            // Let the Set handle the resize event.
            result = theSet.handleResizeEvent(theStage);

            return result;
        }
    }

    /** The resize event callback. */
    protected Mle3dSetResizeCallback m_resizeEventCB = null;

    /**
     * The default constructor.
     * <p>
     * The default position is set to (0,0). The default size is set to 320x480.
     * </p>
     */
    public Mle3dSet()
    {
        // Initialize default property values.
        position.setValue(0, 0);
        size.setValue(320, 480);

        // Add the Set to the Stage.
        MleStage theStage = MleStage.getInstance();
        Mle3dSetRenderer renderer = new Mle3dSetRenderer();
        renderer.setName("3D Set Renderer");
        MleTask id = ((Mle3dStage) theStage).addSet(renderer, this);

        // Remember the task so we can dispose of it later.
        setRenderCBId(id);

        // Create the view and projection matrices using the resize event callback handler.
        handleResizeEvent((Mle3dStage)theStage);
    }

    /* (non-Javadoc)
     * @see com.wizzer.mle.runtime.core.MleSet#init()
     */
    public synchronized void init() throws MleRuntimeException
    {
        // Create a new callback for handling the resize event.
        m_resizeEventCB = new Mle3dSetResizeCallback();

        // Insert resize callback into event dispatch manager.
        m_resizeCBId = MleTitle.getInstance().m_theDispatcher.installEventCB(
                MleEventManager.MLE_SIZE,m_resizeEventCB,this);

        // Bump priority of dispatched callback.
        MleTitle.getInstance().m_theDispatcher.changeCBPriority(
                MleEventManager.MLE_SIZE,m_resizeCBId,
                MleEventManager.MLE_RESIZE_SET_PRIORITY);
    }

    /* (non-Javadoc)
     * @see com.wizzer.mle.runtime.core.MleSet#dispose()
     */
    public synchronized void dispose() throws MleRuntimeException
    {
        // Unschedule the set render() function for the stage only.
        if (m_renderCBId != null)
            MleTitle.getInstance().m_theScheduler.deleteTask(MleTitle.g_theSetPhase,m_renderCBId);

        // Uninstall resize event callback.
        if (m_resizeCBId != null)
            MleTitle.getInstance().m_theDispatcher.uninstallEventCB(
                    MleEventManager.MLE_SIZE,m_resizeCBId);

        // Delete all attached Roles. The dispose() method of each 3D Role will
        // call this set's detach() method and update the m_root pointer.
        // So when the m_root is null, all the attached Roles are
        // disposed of.
        while (m_root != null)
            m_root.dispose();
    }

    /* (non-Javadoc)
    * @see com.wizzer.mle.runtime.core.IMleObject#getProperty(java.lang.String)
    */
    public synchronized Object getProperty(String name)
            throws MleRuntimeException
    {
        if (name != null)
        {
            if (name.equals("position"))
                return position;
            else if (name.equals("size"))
                return size;
        }

        // Specified name does not exist.
        throw new MleRuntimeException("Mle3dSet: Unable to get property " + name + ".");
    }

    /* (non-Javadoc)
     * @see com.wizzer.mle.runtime.core.IMleObject#setProperty(java.lang.String, IMleProp)
     */
    public synchronized void setProperty(String name, IMleProp property)
            throws MleRuntimeException
    {
        if (name != null) {
            try {
                if (name.equals("position")) {
                    // Remember old value.
                    MlVector2 oldValue = new MlVector2(position);

                    // Expecting 2 single-precision floating-point values.
                    DataInputStream in = new DataInputStream(property.getStream());
                    float x = in.readFloat();
                    float y = in.readFloat();
                    position.setValue(x, y);

                    // Notify listeners of the change.
                    MlVector2 newValue = new MlVector2(position);
                    this.notifyPropertyChange("position", oldValue, newValue);

                    return;

                } else if (name.equals("size")) {
                    // Remember old value.
                    MlVector2 oldValue = new MlVector2(size);

                    // Expecting 2 single-precision floating-point values.
                    DataInputStream in = new DataInputStream(property.getStream());
                    float x = in.readFloat();
                    float y = in.readFloat();
                    size.setValue(x, y);

                    // Notify listeners of the change.
                    MlVector2 newValue = new MlVector2(size);
                    this.notifyPropertyChange("size", oldValue, newValue);

                    return;
                }
            } catch (IOException ex) {
                throw new MleRuntimeException("Mle2dSet: Unable to set property " + name + ".");
            }
        }
    }

    /* (non-Javadoc)
     * @see com.wizzer.mle.runtime.core.IMleObject#setPropertyArray(java.lang.String, int, int, java.io.ByteArrayInputStream)
     */
    public synchronized void setPropertyArray(String name, int length, int nElements, ByteArrayInputStream value)
            throws MleRuntimeException
    {
        throw new MleRuntimeException("Mle3dSet: Unable to set property array " + name + ".");
    }

    /**
     * Attach <b>child</b> to <b>parent</b>.
     *
     * @param parent The parent Role to attach to.
     * @param child The child Role to attach.
     *
     * @throws MleRuntimeException This exception is thrown if <b>parent</b>
     * or <b>child</b> is <b>null</b>.
     */
    public synchronized void attachRoles(IMleRole parent, IMleRole child)
            throws MleRuntimeException
    {
        if ((parent != null) && (parent instanceof I3dRole) &&
            (child != null) && (child instanceof I3dRole))
        {
            ((I3dRole) parent).addChild(child);
        } else if ((parent == null) && (child != null) && (child instanceof I3dRole))
        {
            if (m_root == null) {
                // Attach child as root of tree.
                m_root = (I3dRole) child;
            } else {
                // Attach child to root.
                m_root.addChild(child);
            }
        } else
            throw new MleRuntimeException("Mle3dSet: Invalid input parameter.");
    }

    /**
     * Detach specified Role from the role hierarchy.
     *
     * @param child The Role to detach.
     *
     * @throws MleRuntimeException This exception is thrown if <b>curR</b>
     * is <b>null</b>.
     */
    public synchronized void detach(IMleRole child)
            throws MleRuntimeException
    {
        I3dRole role;

        if ((child != null) && (child instanceof I3dRole))
            role = (I3dRole) child;
        else
            throw new MleRuntimeException("Mle3dSet: Invalid input parameter.");

        // Release any resources the role may be holding on to.
        role.dispose();

        // Detach children.
        for (int i = 0; i < role.numChildren(); i++) {
            IMleRole next = role.getChildAt(i);
            this.detach(next);
        }
        role.clearChildren();

        if (role == m_root)
            m_root = null;
    }

    /**
     * This method is used to set the scheduler id for the render method
     * (so that we can remove it during destruction).
     */
    public synchronized void setRenderCBId(MleTask id)
    {
        m_renderCBId = id;
    }

    /*
     * Store the view matrix. This can be thought of as our camera. This matrix transforms
     * world space to eye space; it positions things relative to our eye.
     */
    private float[] m_viewMatrix = new float[16];

    /* Store the projection matrix. This is used to project the scene onto a 2D viewport. */
    private float[] m_projectionMatrix = new float[16];

    /**
     * Retrieve the view matrix for the set.
     *
     * @return An array of floating-point values is returned containing the content of the
     * 4x4 matrix.
     */
    public float[] getViewMatrix()
    {
        return m_viewMatrix;
    }

    /**
     * Retrieve the project matrix for the set.
     *
     * @return An array of floating-point values is returned containing the content of the
     * 4x4 matrix.
     */
    public float[] getProjectionMatrix()
    {
        return m_projectionMatrix;
    }

    /**
     * Handle the resize event.
     *
     * @param theStage The Stage associated with this Set.
     *
     * @return <b>true</b> is returned if the resize event is successfully handled.
     * Otherwise, <b>false</b> is returned.
     */
    public synchronized boolean handleResizeEvent(Mle3dStage theStage)
    {
        // Don't make any OpenGL ES calls if the Stage is not ready.
        if (! theStage.isReady()) return false;

        // Retrieve width and height of the Stage.
        // XXX --  This is not really correct; the width and height should be
        //         obtained from 3D set properties.
        MleSize size = theStage.getSize();

        // Position the eye behind the origin.
        final float eyeX = 0.0f;
        final float eyeY = 0.0f;
        final float eyeZ = -0.5f; // 1.5

        // We are looking toward the distance.
        final float lookX = 0.0f;
        final float lookY = 0.0f;
        final float lookZ = -5.0f;

        // Set our up vector. This is where our head would be pointing were we holding the camera.
        final float upX = 0.0f;
        final float upY = 1.0f;
        final float upZ = 0.0f;

        // Set the view matrix. This matrix can be said to represent the camera position.
        // NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
        // view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
        Matrix.setLookAtM(m_viewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);

        // Set the OpenGL viewport to the same size as the surface.
        GLES20.glViewport(0, 0, (int)size.getWidth(), (int)size.getHeight());

        // Create a new perspective projection matrix. The height will stay the same
        // while the width will vary as per aspect ratio.
        final float ratio = (float) size.getWidth() / size.getHeight();
        final float left = -ratio;
        final float right = ratio;
        final float bottom = -1.0f;
        final float top = 1.0f;
        final float near = 1.0f;
        final float far = 10.0f;

        // Set the projection matrix.
        Matrix.frustumM(m_projectionMatrix, 0, left, right, bottom, top, near, far);

        // ToDo: place the projection and view (camera) matrices in the scene graph
        // so that the role does not have to directly have a handle to these.
        return true;
    }

    /**
     * Initialize rendering on the Set.
     */
    @Override
    public void initRender()
        throws MleRuntimeException
    {
        // Initialize the scene graph.
        m_root.initRender();
    }

    /**
     * Render the Set.
     */
    @Override
    public void render()
        throws MleRuntimeException
    {
        // Note: if required, the view and projection matrices may be obtained by the Role
        // for rendering. Use getViewMatrix() and getProjectionMatrix, respectively.

        // Render the scene graph.
        m_root.render();
    }

    // Camera position parameters.

    // ToDo: implement.
    int setCameraTransform(MlTransform transform) { return 0; };

    // ToDo: implement.
    void getCameraTransform(MlTransform transform) {};

    // ToDo: implement.
    int setCameraPosition(MlVector3 position) {return 0; };

    // ToDo: implement.
    void getCameraPosition(MlVector3 position) {};

    // ToDo: implement.
    int setCameraOrientation(MlRotation orientation) {return 0;};

    // ToDo: implement.
    void getCameraOrientation(MlRotation orientation) {};

    // ToDo: implement.
    int setCameraNearClipping(MlScalar nearPlane) {return 0; };

    // ToDo: implement.
    MlScalar getCameraNearClipping() {return null;};

    // ToDo: implement.
    int setCameraFarClipping(MlScalar farPlane) {return 0;};

    // ToDo: implement.
    MlScalar getCameraFarClipping() {return null; };

    // ToDo: implement.
    int setCameraAspect(MlScalar aspect) {return 0; };

    // ToDo: implement.
    MlScalar getCameraAspect() {return null; };
}