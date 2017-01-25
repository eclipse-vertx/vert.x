/*
 *  Copyright (c) 2011-2017 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.cli;

import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

/**
 * @author <a href="mailto:ruslan.sennov@gmail.com">Ruslan Sennov</a>
 */
public class OptionTest extends VertxTestBase {

    @Test
    public void createAndCopy() {
        Option option = new Option()
                .setArgName(TestUtils.randomAlphaString(100))
                .addChoice(TestUtils.randomAlphaString(100))
                .setDefaultValue(TestUtils.randomAlphaString(100))
                .setDescription(TestUtils.randomAlphaString(100))
                .setFlag(TestUtils.randomBoolean())
                .setHelp(TestUtils.randomBoolean())
                .setHidden(TestUtils.randomBoolean())
                .setLongName(TestUtils.randomAlphaString(100))
                .setMultiValued(TestUtils.randomBoolean())
                .setRequired(TestUtils.randomBoolean())
                .setShortName(TestUtils.randomAlphaString(100))
                .setSingleValued(TestUtils.randomBoolean());

        Option copy = new Option(option);
        assertEquals(copy.getArgName(), option.getArgName());
        assertEquals(copy.getChoices(), option.getChoices());
        assertEquals(copy.getDefaultValue(), option.getDefaultValue());
        assertEquals(copy.getDescription(), option.getDescription());
        assertEquals(copy.isFlag(), option.isFlag());
        assertEquals(copy.isHelp(), option.isHelp());
        assertEquals(copy.isHidden(), option.isHidden());
        assertEquals(copy.getLongName(), option.getLongName());
        assertEquals(copy.isMultiValued(), option.isMultiValued());
        assertEquals(copy.isRequired(), option.isRequired());
        assertEquals(copy.getShortName(), option.getShortName());
        assertEquals(copy.isSingleValued(), option.isSingleValued());

        copy = new Option(option.toJson());
        assertEquals(copy.getArgName(), option.getArgName());
        assertEquals(copy.getChoices(), option.getChoices());
        assertEquals(copy.getDefaultValue(), option.getDefaultValue());
        assertEquals(copy.getDescription(), option.getDescription());
        assertEquals(copy.isFlag(), option.isFlag());
        assertEquals(copy.isHelp(), option.isHelp());
        assertEquals(copy.isHidden(), option.isHidden());
        assertEquals(copy.getLongName(), option.getLongName());
        assertEquals(copy.isMultiValued(), option.isMultiValued());
        assertEquals(copy.isRequired(), option.isRequired());
        assertEquals(copy.getShortName(), option.getShortName());
        assertEquals(copy.isSingleValued(), option.isSingleValued());
    }
}
