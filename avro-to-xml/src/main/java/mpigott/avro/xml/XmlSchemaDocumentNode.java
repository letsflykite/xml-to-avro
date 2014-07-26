package mpigott.avro.xml;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the state machine as a tree with the current iteration of each
 * node, and additional state information for All and Sequence groups.
 *
 * If the node represents a sequence group, we need to know which child we
 * visit next.  Once we visit a node the maximum number of occurrences (or
 * we visit the minimum number of occurrences and the element name does not
 * match), this index will be incremented to the next child.
 *
 * This class is package-protected, and not private, to allow an external
 * graph generator to build a visualization of the tree.
 */
final class XmlSchemaDocumentNode {
  XmlSchemaDocumentNode(SchemaStateMachineNode stateMachineNode) {
    set(null, stateMachineNode);
  }

  XmlSchemaDocumentNode(
      XmlSchemaDocumentNode parent,
      SchemaStateMachineNode stateMachineNode) {

    set(parent, stateMachineNode);
  }

  SchemaStateMachineNode getStateMachineNode() {
    return stateMachineNode;
  }

  XmlSchemaDocumentNode getParent() {
    return parent;
  }

  List<XmlSchemaDocumentNode> getChildren() {
    return children;
  }

  int getCurrIteration() {
    return currIteration;
  }

  int getCurrPositionInSequence() {
    return currPositionInSeqGroup;
  }

  /**
   * Indicates whether an element has text in it.
   */
  boolean getReceivedContent() {
    return receivedContent;
  }

  void setCurrIteration(int newIteration) {
    currIteration = newIteration;
  }

  void setCurrPositionInSequence(int newPosition) {
    currPositionInSeqGroup = newPosition;
  }

  void setReceivedContent(boolean receivedContent) {
    this.receivedContent = receivedContent;
  }

  final void set(
      XmlSchemaDocumentNode parent,
      SchemaStateMachineNode stateMachineNode) {

    if ((this.stateMachineNode.getPossibleNextStates() == null)
        || this.stateMachineNode.getPossibleNextStates().isEmpty()) {
      this.children = null;

    } else {
      this.children =
          new ArrayList<XmlSchemaDocumentNode>(
              this.stateMachineNode.getPossibleNextStates().size() );
    }

    this.parent = parent;
    this.stateMachineNode = stateMachineNode;
    this.currIteration = 0;
    this.currPositionInSeqGroup = -1;
    this.receivedContent = false;
  }

  private SchemaStateMachineNode stateMachineNode;
  private XmlSchemaDocumentNode parent;
  private List<XmlSchemaDocumentNode> children;

  private int currIteration;
  private int currPositionInSeqGroup;
  private boolean receivedContent;
}