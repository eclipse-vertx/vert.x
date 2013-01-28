# Copyright 2011 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import vertx

def deployit(count):
    def handler(deploy_id):
        print "deployed ", count
        if deploy_id is not None:
            undeployit(deploy_id, count)
    #child_name = 'child%d.py' % count
    print "deploying ", count
    vertx.deploy_verticle("child.py", handler=handler)

def undeployit(deploy_id, count):
    def handler():
        newcount = count + 1
        if newcount < 10:
            deployit(newcount)
        else:
            print "done!"
    vertx.undeploy_verticle(deploy_id, handler=handler)

deployit(0)

