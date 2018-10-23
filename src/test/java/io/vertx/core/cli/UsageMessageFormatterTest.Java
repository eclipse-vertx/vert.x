/*
 * Copyright 2018 Diffblue Limited
 *
 * Diffblue Limited licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vertx.core.cli;

import io.vertx.core.cli.UsageMessageFormatter;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UsageMessageFormatterTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void rtrimInputNotNullOutputNotNull3() {

    // Arrange
    final String s = "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ";

    // Act
    final String retval = UsageMessageFormatter.rtrim(s);

    // Assert result
    Assert.assertEquals("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", retval);
  }

  @Test
  public void isNullOrEmptyInputNotNullOutputTrue() {

    // Arrange
    final String s = "    ";

    // Act
    final boolean retval = UsageMessageFormatter.isNullOrEmpty(s);

    // Assert result
    Assert.assertEquals(true, retval);
  }

  @Test
  public void isNullOrEmptyInputNotNullOutputFalse() {

    // Arrange
    final String s = "    !";

    // Act
    final boolean retval = UsageMessageFormatter.isNullOrEmpty(s);

    // Assert result
    Assert.assertEquals(false, retval);
  }

  @Test
  public void findWrapPosInputNotNullPositiveNegativeOutputPositive() {

    // Arrange
    final String text = "\t\n\t\t\u0000\u0000";
    final int width = 1_073_741_828;
    final int startPos = -1_073_741_823;

    // Act
    final int retval = UsageMessageFormatter.findWrapPos(text, width, startPos);

    // Assert result
    Assert.assertEquals(2, retval);
  }

  @Test
  public void findWrapPosInputNotNullNegativeNegativeOutputPositive2() {

    // Arrange
    final String text = "\t\n\u000b\u000b\n\n";
    final int width = -2_147_483_644;
    final int startPos = -2_147_483_647;

    // Act
    final int retval = UsageMessageFormatter.findWrapPos(text, width, startPos);

    // Assert result
    Assert.assertEquals(5, retval);
  }

  @Test
  public void findWrapPosInputNotNullNegativeNegativeOutputPositive() {

    // Arrange
    final String text = "       !JJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJ";
    final int width = -2_147_483_642;
    final int startPos = -2_147_483_647;

    // Act
    final int retval = UsageMessageFormatter.findWrapPos(text, width, startPos);

    // Assert result
    Assert.assertEquals(6, retval);
  }

  @Test
  public void findWrapPosInputNotNullNegativeNegativeOutputPositive3() {

    // Arrange
    final String text = "\t\n\t\t\r\u0000";
    final int width = -2_147_483_644;
    final int startPos = -2_147_483_647;

    // Act
    final int retval = UsageMessageFormatter.findWrapPos(text, width, startPos);

    // Assert result
    Assert.assertEquals(4, retval);
  }

  @Test
  public void findWrapPosInputNotNullPositiveNegativeOutputPositive2() {

    // Arrange
    final String text = "\b\t\b\n\t\t\t\t";
    final int width = 1;
    final int startPos = -2_147_483_636;

    // Act
    final int retval = UsageMessageFormatter.findWrapPos(text, width, startPos);

    // Assert result
    Assert.assertEquals(2, retval);
  }

  @Test
  public void findWrapPosInputNotNullNegativePositiveOutputNegative() {

    // Arrange
    final String text = "????";
    final int width = -8189;
    final int startPos = 2_146_564_369;

    // Act
    final int retval = UsageMessageFormatter.findWrapPos(text, width, startPos);

    // Assert result
    Assert.assertEquals(-1, retval);
  }

  @Test
  public void findWrapPosInputNotNullNegativePositiveOutputPositive() {

    // Arrange
    final String text = "  ?";
    final int width = -8191;
    final int startPos = 8192;

    // Act
    final int retval = UsageMessageFormatter.findWrapPos(text, width, startPos);

    // Assert result
    Assert.assertEquals(1, retval);
  }

  @Test
  public void findWrapPosInputNotNullPositivePositiveOutputPositive() {

    // Arrange
    final String text = "      ";
    final int width = 1;
    final int startPos = 1;

    // Act
    final int retval = UsageMessageFormatter.findWrapPos(text, width, startPos);

    // Assert result
    Assert.assertEquals(2, retval);
  }

  @Test
  public void findWrapPosInputNotNullZeroPositiveOutputPositive() {

    // Arrange
    final String text = "      ";
    final int width = 0;
    final int startPos = 2;

    // Act
    final int retval = UsageMessageFormatter.findWrapPos(text, width, startPos);

    // Assert result
    Assert.assertEquals(2, retval);
  }

  @Test
  public void findWrapPosInputNotNullPositiveNegativeOutputStringIndexOutOfBoundsException() {

    // Arrange
    final String text = "";
    final int width = 1_073_741_824;
    final int startPos = -1_073_741_826;

    // Act
    thrown.expect(StringIndexOutOfBoundsException.class);
    UsageMessageFormatter.findWrapPos(text, width, startPos);

    // Method is not expected to return due to exception thrown
  }
}
