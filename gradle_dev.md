<!--
This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
-->

[TOC]

# Developing Vert.x modules with Gradle

In this HOWTO we'll show you how to develop a Vert.x project using Gradle.

# Fork and clone the template project

We provide a template [Gradle project](https://github.com/vert-x/vertx-gradle-template) which you can clone to get you started.

Fork it in github, then clone it locally

    git clone https://github.com/<your_user_name>/vertx-gradle-template.git my-vertx-module

Where `my-vertx-module` is the name you want to give your project.

Let's run the tests to make sure everything is working

    cd my-vertx-module
    ./gradlew test

You should use the Gradle Wrapper (`./gradlew`) to run all Gradle tasks. Take a look at `build.gradle` for a list of the available tasks.

# Outputs

The outputs of the project are:

* The Vert.x module zip file.
* If `produceJar = true` in `gradle.properties` A jar that corresponds to the module will also be produced. This is useful when you have another project which depends on the classes from your module, as it allows you to add it as a dependency to your other project.

The outputs are created in the `build` directory as normal.

# Other useful Gradle tasks

Open `build.gradle` and take a look at the comments there for a list of useful tasks supported by the build script.

# Setup your IDE

You can use the `idea` and `eclipse` Gradle plugins to create the project files for your IDE

    ./gradlew idea

Or

    ./gradlew eclipse

Once the IDE files have been created you can open the project in your IDE.

Note: You can run the `idea` or `eclipse` tasks again if you change your project dependencies - in this way the IDE project files will be brought up-to-date.

> "You may have to tell your IDE to use Java source compatibility level of Java 7, as Maven seems to default to Java 5 (!)"

# Changing the dependencies of your project

If your project needs a third party jar to build and you want to include it in the `lib` directory of your module you can add the dependency in the `dependencies` section of `build.gradle` with a type of `compile`.

If you don't want it to be included in the `lib` directory you should add it as `provided`.

Once you've changed your dependencies just run `./gradlew idea` or `./gradlew eclipse` again to update your IDE project files with the new dependencies.



# Next steps

Now you've got the project all set-up and running, it's time to [explore the project](example_project.html) itself.


