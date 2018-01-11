/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
