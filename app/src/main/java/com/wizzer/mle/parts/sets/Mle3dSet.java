package com.wizzer.mle.parts.sets;

import com.wizzer.mle.math.MlVector2;
import com.wizzer.mle.parts.j3d.min3d.Node;
import com.wizzer.mle.parts.j3d.sets.I3dSet;
import com.wizzer.mle.parts.roles.Mle3dRole;
import com.wizzer.mle.parts.stages.Mle3dStage;
import com.wizzer.mle.runtime.MleTitle;
import com.wizzer.mle.runtime.core.IMleCallbackId;
import com.wizzer.mle.runtime.core.IMleProp;
import com.wizzer.mle.runtime.core.MleRole;
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
     * The <b>m_root</p> is the root of the scene graph.
     */
    protected Mle3dRole m_root = null;

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

        // Create the offscreen buffer using the resize event callback handler.
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
    public synchronized void attachRoles(MleRole parent, MleRole child)
            throws MleRuntimeException
    {
        Mle3dRole parentR, childR;

        if ((parent != null) && (parent instanceof Mle3dRole) &&
            (child != null) && (child instanceof Mle3dRole))
        {
            parentR = (Mle3dRole) parent;
            childR = (Mle3dRole) child;
        } else
            throw new MleRuntimeException("Mle3dSet: Invalid input parameter.");

        parentR.addChild(childR);
    }

    /**
     * Detach specified Role from the role hierarchy.
     *
     * @param child The Role to detach.
     *
     * @throws MleRuntimeException This exception is thrown if <b>curR</b>
     * is <b>null</b>.
     */
    public synchronized void detach(MleRole child)
            throws MleRuntimeException
    {
        Mle3dRole role;

        if ((child != null) && (child instanceof Mle3dRole))
            role = (Mle3dRole) child;
        else
            throw new MleRuntimeException("Mle3dSet: Invalid input parameter.");

        // Release any resources the role may be holding on to.
        role.dispose();

        // Detach children.
        for (int i = 0; i < role.numChildren(); i++) {
            MleRole next = role.getChildAt(i);
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
        // Retrieve width and height of the Stage.
        // XXX --  This is not really correct; the width and height should be
        //         obtained from 3D set properties.
        MleSize size = theStage.getSize();

        /* ToDo: Need to determine how to deal with the resize event.
        // Get the Stage's offscreen buffer.
        MleBitmap stageBuffer = theStage.getPixelBuffer();

        // Clean up the old offscreen buffer.
        if (this.m_imageBuffer != null)
            this.m_imageBuffer.dispose();

        // Create an offscreen buffer that shares the one used by the Stage.
        this.m_imageBuffer = new MleBitmap(stageBuffer.getBitmap());
        */

        return true;
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