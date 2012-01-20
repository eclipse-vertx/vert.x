function func2() {
  try {
    load('does-not-exist.js');
    tu.azzert(false, 'Should throw exception');
  } catch (err) {
    // OK
  }
  return "bar";
}
