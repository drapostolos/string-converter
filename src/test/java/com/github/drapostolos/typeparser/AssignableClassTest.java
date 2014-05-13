package com.github.drapostolos.typeparser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class AssignableClassTest extends TestBase {

    @Test
    public void canParseSubclassOfAssignableTypeParser() throws Exception {
        // GIVEN
        Parser<MyBaseClass> typeParser = new Parser<MyBaseClass>() {

            @Override
            public MyBaseClass parse(String input, ParserHelper helper) {
                if (input.equals("1")) {
                    return new MyClass1();
                }
                return new MyClass2();
            }
        };
        TypeParser parser = TypeParser.newBuilder()
                .registerParserForTypesAssignableTo(MyBaseClass.class, typeParser)
                .build();

        // THEN
        assertThat(parser.parse("1", MyClass1.class)).isInstanceOf(MyClass1.class);
        assertThat(parser.parse("2", MyClass2.class)).isInstanceOf(MyClass2.class);
        assertThat(parser.isTargetTypeParsable(MyClass1.class)).isTrue();
        assertThat(parser.isTargetTypeParsable(MyBaseClass.class)).isTrue();
    }

}
