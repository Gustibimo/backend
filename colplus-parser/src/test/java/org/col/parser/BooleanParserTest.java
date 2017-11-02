package org.col.parser;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.List;

/**
 *
 */
public class BooleanParserTest extends ParserTestBase<Boolean> {

  public BooleanParserTest() {
    super(BooleanParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(true, "true");
    assertParse(true, "-1-");
    assertParse(true, "yes!");
    assertParse(true, " t    ");
    assertParse(true, "T");
    assertParse(true, "si");
    assertParse(true, "ja");
    assertParse(true, "oui");
    assertParse(true, "wahr");

    assertParse(false,"f");
    assertParse(false,"f");
    assertParse(false,"no");
    assertParse(false,"nein");
  }

  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("t ru e", "a", "2");
  }

}