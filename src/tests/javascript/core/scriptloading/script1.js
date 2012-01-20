function func1() {
  load('core/scriptloading/script2.js');
  tu.azzert(func2() === 'bar');
  return "foo";
}
