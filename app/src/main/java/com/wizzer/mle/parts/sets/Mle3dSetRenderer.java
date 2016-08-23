package com.wizzer.mle.parts.sets;

import com.wizzer.mle.parts.j3d.MleRenderEngine;
import com.wizzer.mle.parts.stages.Mle3dStage;

/**
 * Created by msm on 8/23/16.
 */
public class Mle3dSetRenderer extends MleRenderEngine
{
    /**
     * The default constructor.
     */
    public Mle3dSetRenderer()
    {
        super();

        init();
    }

    /**
     * A constructor that initializes the name of the renderer thread.
     *
     * @param name The name of the renderer.
     */
    public Mle3dSetRenderer(String name)
    {
        super(name);

        init();
    }

    // Initialize the renderer.
    private void init()
    {}

    /**
     * Thread execution.
     * <p>
     * This method is used to traverse the Role scene graph and render each
     * 2D Role.
     * </p>
     */
    public void run() {
        // Get the Stage from the call data.
        Mle3dStage theStage = (Mle3dStage) m_callData;
        // Get the Set from the client data.
        Mle3dSet theSet = (Mle3dSet) m_clientData;
    }
}
