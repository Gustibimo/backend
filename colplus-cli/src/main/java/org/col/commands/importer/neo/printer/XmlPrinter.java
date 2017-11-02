package org.col.commands.importer.neo.printer;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.xml.XmlEscapers;
import org.col.api.vocab.Rank;
import org.col.commands.importer.neo.model.Labels;
import org.col.commands.importer.neo.model.NeoProperties;
import org.col.commands.importer.neo.model.RelType;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.parboiled.common.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;

/**
 * A handler that can be used with the TaxonWalker to print a neo4j taxonomy in a simple nested text structure.
 */
public class XmlPrinter implements TreePrinter {
  private final Writer writer;
  private LinkedList<String> parents = Lists.newLinkedList();

  public XmlPrinter(Writer writer) {
    this.writer = writer;
  }

  @Override
  public void start(Node n) {
    open(n, n.hasLabel(Labels.SYNONYM));
  }

  @Override
  public void end(Node n) {
    if (!n.hasLabel(Labels.SYNONYM)) {
      close(n);
    }
  }

  private void open(Node n, boolean close) {
    try {
      String name = NeoProperties.getScientificName(n);
      Rank rank = Rank.values()[(Integer) n.getProperty(NeoProperties.RANK, Rank.UNRANKED.ordinal())];
      name = escapeTag(name);

      writer.write(StringUtils.repeat(' ', parents.size() * 2));
      writer.write("<");
      writer.write(name);
      printAttr("name", name);
      printAttr("rank", rank.name().toLowerCase());
      if (n.hasRelationship(RelType.BASIONYM_OF, Direction.OUTGOING)) {
        printAttr("basionym", "true");
      }
      if (n.hasLabel(Labels.SYNONYM)) {
        printAttr("synonym", "true");
      }
      if (close) {
        writer.write(" /");
      } else {
        parents.add(name);
      }
      writer.write(">");
      writer.write("\n");
    } catch (IOException e) {
      Throwables.propagate(e);
    }
  }

  private void close(Node n) {
    try {
      writer.write("</");
      writer.write(parents.removeLast());
      writer.write(">");
      writer.write("\n");
    } catch (IOException e) {
      Throwables.propagate(e);
    }
  }

  private void printAttr(String attr, String value) throws IOException {
    if (!Strings.isNullOrEmpty(value)) {
      writer.write(" ");
      writer.write(attr);
      writer.write("=\"");
      writer.write(XmlEscapers.xmlAttributeEscaper().escape(value));
      writer.write("\"");
    }
  }

  private String escapeTag(String x) {
    return x.replaceAll("[^a-zA-Z0-9_.-]", "_");
  }

  @Override
  public void close() {

  }
}