# Eclipse Vert.x Community Contributing Guide

This document describes a very simple process suitable for most projects in the Vert.x ecosystem. Projects are encouraged to adopt this whether they are hosted in the Vert.x Organization or not.

The goal of this document is to create a contribution process that:

* Encourages new contributions.
* Encourages contributors to remain involved.
* Avoids unnecessary processes and bureaucracy whenever possible.
* Creates a transparent decision-making process which makes it clear how contributors can be involved in decision-making.

## Vocabulary

* A **Contributor** is any individual creating or commenting on an issue or pull request.
* A **Committer** is a subset of contributors who have been given write access to the repository.


# Logging Issues

Log an issue for any problem you might have. When in doubt, log an issue, any additional policies about what to include will be provided in the responses. The only exception is security disclosures which should be sent [privately](vertx-enquiries@googlegroups.com).

Committers may direct you to another repository, ask for additional clarifications, and add appropriate info before the issue is addressed.

For questions that are not an issue with the code, e.g.: questions related to usage of the project, it is recommended that they are sent to the [community group](https://groups.google.com/forum/#!forum/vertx). This exposes the question to the whole community, which increases the chance of getting faster responses than just from contributors and committers.

# Contributions

Any change that would roughly be more than 10 lines (non-trivial change) to resources in this repository must be through pull requests. This applies to all changes to documentation, code, binary files, etc. Even long term committers must use pull requests. Changes less than 10 lines or so (e.g.: correcting typos, small changes to configuration and such-like) can be make directly on a master branch. For a more detailed development process, please consult the article [Development Process](https://github.com/vert-x3/wiki/wiki/Development-Process) in the [wiki](https://github.com/vert-x3/wiki/wiki).

No pull request can be merged without being reviewed.

It is expected that all contributors to Eclipse Vert.x organization sign the [Eclipse Contributor Agreement](http://www.eclipse.org/legal/ECA.php). In order to sign the ECA a contributor is required to have an Eclipse Foundation user id and read and digitally sign the following [document](http://www.eclipse.org/contribute/cla). Digitally signing is as simple as read the document and submit the "I agree" button.

There is an additional "sign off" process for contributions to land. All commits to code in the Eclipse Vert.x organization **MUST** be signed off. This is done when committing from git passing the extra argument `-s` e.g.:

```
git commit -s -m "Shave the yak some more"
```

For non-trivial contributions, pull requests should sit for at least 36 hours to ensure that contributors in other time zones have time to review. Consideration should also be given to weekends and other holiday periods to ensure active committers all have reasonable time to become involved in the discussion and review process if they wish.

The default for each contribution is that it is accepted once no committer has an objection. During review committers may also request that a specific contributor who is most versed in a particular area gives a "LGTM" before the PR can be merged.

For more info, see [here](http://wiki.eclipse.org/Development_Resources/Contributing_via_Git). Once all issues brought by committers are addressed it can be landed by any committer.

In the case of an objection being raised in a pull request by another committer, all involved committers should seek to arrive at a consensus by way of addressing concerns being expressed by discussion, compromise on the proposed change, or withdrawal of the proposed change.

If a contribution is controversial and committers cannot agree about how to get it to land or if it should land then it should be escalated to the [committers group](https://groups.google.com/forum/#!forum/vertx-committers). Members of the developers group should regularly discuss pending contributions in order to find a resolution. It is expected that only a small minority of issues be brought to the group for resolution and that discussion and compromise among committers be the default resolution mechanism.

# Copyright notice headers

It is important that all source code files have correct copyright notice headers.

You can opt for the general-purpose form:

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

Or you may choose to explicitly mention all contributors in the declaration, like in:

    /*
     * Copyright (c) {date} {owner}[ and others]
     *
     * This program and the accompanying materials are made available under the
     * terms of the Eclipse Public License 2.0 which is available at
     * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
     * which is available at https://www.apache.org/licenses/LICENSE-2.0.
     *
     * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
     */

Check https://www.eclipse.org/projects/handbook/#legaldoc

# Becoming a Contributor

Becoming a contributor to the project should be an easy step. In order to reduce that barrier new contributors should look for issues tagged as:

* `Quick Win`
* `Help Wanted`

These tags should be applied to issues that are relatively simple to fix but not critical to be handled by the current group of committers. Once you pick such an issue to work on a discussion with a committer should happen on the issue itself and the committer should guide you with the mentoring/onboarding process.


# Becoming a Committer

All contributors who land a non-trivial contribution should be on-boarded in a timely manner, and added as a committer, and be given write access to the repository.

Committers are expected to follow this policy and continue to send pull requests, go through proper review, and have other committers merge their pull requests.


# Mentoring / Onboarding

Committers should help mentor/on-board new contributors. The workflow should roughly follow this items:

* suggest the new contributor to join the [development group](https://groups.google.com/forum/#!forum/vertx-dev)
* ask for participation on github issue discussions
* suggest to keep in touch with on the dev group on periodic way (e.g.: weekly)
* help finding a good mentor if one is not well versed in a specific area of the project
* suggest progress reporting
* assist with creating an Eclipse user id and ECA sign


# Technical Conflict Process

The Vert.x project uses a "consensus seeking" process for issues that are escalated to the [committers group](https://github.com/orgs/vert-x3/people). The group tries to find a resolution that has no open objections among the members. If a consensus cannot be reached that has no objections then the Project Lead should decide on which approach to take. It is also expected that the majority of decisions made by the group are via a consensus seeking process and that voting is only used as a last-resort.

Resolution may involve returning the issue to committers with suggestions on how to move forward towards a consensus. It is not expected that a meeting of the group will resolve all issues on its agenda during that meeting and may prefer to continue the discussion happening among the committers.

Members can be added to the group at any time. Any committer can nominate another committer to the group and the group uses its standard consensus seeking process to evaluate whether or not to add this new member. Members who do not participate consistently at the level of a majority of the other members are expected to resign.
