include Java

puts "starting scratch"


java_map = java.util.HashMap.new

java_map.put("foo", "bar")

puts "java map is #{java_map}"

puts "foo is #{java_map["foo"]}"


Proc.new{ |b|
  if b
    #do something
  end
}




