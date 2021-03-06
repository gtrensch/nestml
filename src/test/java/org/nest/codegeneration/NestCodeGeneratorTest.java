/*
 * Copyright (c) 2015 RWTH Aachen. All rights reserved.
 *
 * http://www.se-rwth.de/
 */
package org.nest.codegeneration;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.nest.base.GenerationBasedTest;
import org.nest.utils.FilesHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates entire NEST implementation for several NESTML models. Uses MOCKs or works with models without ODEs.
 *
 * @author plotnikov
 */
public class NestCodeGeneratorTest extends GenerationBasedTest {
  private static final String PSC_MODEL_WITH_ODE = "models/iaf_psc_alpha.nestml";
  private static final String PSC_MODEL_THREE_BUFFERS = "src/test/resources/codegeneration/iaf_psc_alpha_three_buffers.nestml";
  private static final String COND_MODEL_WITH_ODE = "models/iaf_cond_alpha.nestml";

  @Before
  public void cleanUp() {
    FilesHelper.deleteFilesInFolder(CODE_GEN_OUTPUT);
  }


  @Test
  public void testPSCModelWithOde() {
    final ArrayList<String> psc_models = Lists.newArrayList(PSC_MODEL_WITH_ODE);
    psc_models.forEach(this::checkCocos);
    psc_models.forEach(this::invokeCodeGenerator);
    generateNESTModuleCode(psc_models);
  }

  @Test
  public void testCondModel() {
    final ArrayList<String> cond_models_with_shapes = Lists.newArrayList(COND_MODEL_WITH_ODE);
    cond_models_with_shapes.forEach(this::checkCocos);
    cond_models_with_shapes.forEach(this::invokeCodeGenerator);
    generateNESTModuleCode(cond_models_with_shapes);
  }

  @Test
  public void testPSCModelWithThreeBuffers() {
    final ArrayList<String> model_with_multiple_buffers = Lists.newArrayList(PSC_MODEL_THREE_BUFFERS);
    model_with_multiple_buffers.forEach(this::checkCocos);
    model_with_multiple_buffers.forEach(this::invokeCodeGenerator);
    generateNESTModuleCode(model_with_multiple_buffers);
  }

}
