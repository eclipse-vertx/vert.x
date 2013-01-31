require "vertx"


def deploy_it(count)
  Vertx::deploy_verticle("child.rb") do |deploy_id|
    puts "deployed #{count}"
    undeploy_it(deploy_id, count)
  end
end

def undeploy_it(deploy_id, count)
  #puts "undeploying #{deploy_id}"
  Vertx::undeploy_verticle(deploy_id) do
    # puts "undeployed"
    count = count + 1
    if count < 10000
      deploy_it(count)
    else
      puts "done!"
    end
  end
end

deploy_it(0)

#Vertx::deploy_verticle("child1.rb")
#Vertx::deploy_verticle("child2.rb")
#Vertx::deploy_verticle("child3.rb")