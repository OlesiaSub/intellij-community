// "Remove 'toString()' call" "true"

fun foo(s: String) = s

fun bar() = foo("a${"b".toString()<caret>}")