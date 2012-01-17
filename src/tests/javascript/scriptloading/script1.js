function func1() {
  load('scriptloading/script2.js');
  tu.azzert(func2() === 'bar');
  return "foo";
}
