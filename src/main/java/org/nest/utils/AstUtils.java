/*
 * Copyright (c) 2015 RWTH Aachen. All rights reserved.
 *
 * http://www.se-rwth.de/
 */
package org.nest.utils;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import de.monticore.ast.ASTNode;
import de.monticore.symboltable.Scope;
import de.se_rwth.commons.SourcePosition;
import de.se_rwth.commons.Util;
import org.apache.commons.io.FileUtils;
import org.nest.nestml._ast.*;
import org.nest.nestml._parser.NESTMLParser;
import org.nest.nestml._symboltable.NESTMLScopeCreator;
import org.nest.nestml._symboltable.symbols.TypeSymbol;
import org.nest.nestml._symboltable.symbols.VariableSymbol;
import org.nest.nestml._symboltable.typechecking.Either;
import org.nest.nestml._symboltable.unitrepresentation.SIData;
import org.nest.nestml._symboltable.unitrepresentation.UnitRepresentation;
import org.nest.nestml._visitor.NESTMLInheritanceVisitor;
import org.nest.nestml._visitor.UnitsSIVisitor;
import org.nest.nestml.prettyprinter.ExpressionsPrettyPrinter;
import org.nest.nestml.prettyprinter.NESTMLPrettyPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.*;
import static java.lang.Math.pow;
import static java.util.stream.Collectors.toList;
import static org.nest.nestml._symboltable.predefined.PredefinedTypes.getType;
import static org.nest.nestml._symboltable.symbols.VariableSymbol.resolve;

/**
 * Helper class containing common operations concerning ast nodes.
 * 
 * @author plotnikov, oberhoff
 */
public final class AstUtils {
  private final static NESTMLParser parser = new NESTMLParser();
  /**
   * Returns the unambiguous parent of the {@code queryNode}. Uses an breadthfirst traverse approach to collect nodes in the error order
   * @param queryNode The node direct parent of the given node
   * @param root The node that is an ancestor of the {@code queryNode}
   *
   * @return Parent of the queryNode or an Absent value if the parent was not found
   */
  public static Optional<ASTNode> getParent(ASTNode queryNode, ASTNode root) {
    checkNotNull(queryNode);
    checkNotNull(root);

    final Deque<ASTNode> successors = Queues.newArrayDeque();

    final Iterable<ASTNode> tmp = Util.preOrder(root, ASTNode::get_Children);

    successors.add(root);
    for (ASTNode parentCandidate:tmp) {
      if (parentCandidate.get_Children().contains(queryNode)) {
        return Optional.of(parentCandidate);
      }
    }

    return Optional.empty();
  }

  /**
   * Finds the first node of the required type
   * @param root The root node from where the search starts
   * @param clazz The required type
   * @return The fist node of the required type or empty value.
   */
  @SuppressWarnings("unchecked") // checked by reflection
  public static <T> Optional<T> getAny(ASTNode root, Class<T> clazz) {
    final Iterable<ASTNode> nodes = Util.preOrder(root, ASTNode::get_Children);

    for (ASTNode node:nodes) {
      if (clazz.isInstance(node)) {
        // it is checked by the if-conditions. only T types are handled
        return Optional.of( (T) node);
      }
    }

    return Optional.empty();
  }

  /**
   * Finds the first node of the required type
   * @param root The root node from where the search starts
   * @param clazz The required type
   * @return The list with all nodes of the required type.
   */
  @SuppressWarnings("unchecked") // checked by reflection
  public static <T> List<T> getAll(ASTNode root, Class<T> clazz) {
    final Iterable<ASTNode> nodes = Util.preOrder(root, ASTNode::get_Children);
    final List<T> resultList = Lists.newArrayList();
    for (ASTNode node:nodes) {
      if (clazz.isInstance(node)) {
        // it is checked by the if-conditions. only T types are handled
         resultList.add((T) node);
      }
    }

    return resultList;
  }

  /**
   * Retrieves all successor nodes of the given node.
   * @param root The root node to start from
   * @return The list with all successors of the given node.
   */
  @SuppressWarnings("unchecked") // checked by reflection
  public static List<ASTNode> getSuccessors(ASTNode root) {
    final Iterable<ASTNode> nodes = Util.preOrder(root, ASTNode::get_Children);

    return Lists.newArrayList(nodes);
  }

  /**
   * Returns all variables defined in the tree starting from the astNode.
   */
  public static List<String> getVariablesNamesFromAst(final ASTNESTMLNode astNode) {
    final VariablesCollector variablesCollector = new VariablesCollector();
    astNode.accept(variablesCollector);
    return variablesCollector.getVariableNames();
  }

  /**
   * Returns all aliases which are used in the tree beginning at astNode
   * This method must remain public. It is used in Freemarker-templates.
   */
  public static List<VariableSymbol> getAliasSymbols(final ASTNESTMLNode astNode) {
    checkState(astNode.getEnclosingScope().isPresent(), "Run symbol table creator");
    final Scope scope = astNode.getEnclosingScope().get();
    final List<String> names = getVariablesNamesFromAst(astNode);
    return names.stream()
        .filter(name -> !name.contains("'"))
        .map(variableName -> resolve(variableName, scope)) // the variable existence checked by the context condition
        .filter(VariableSymbol::isFunction)
        .collect(toList());
  }

  public static Optional<VariableSymbol> getVectorizedVariable(
      final ASTNode astNode,
      final Scope scope) {
    final List<ASTVariable> variables = AstUtils
        .getAll(astNode, ASTVariable.class).stream()
        .filter(astVariable -> VariableSymbol.resolveIfExists(astVariable.toString(), scope).isPresent())
        .collect(Collectors.toList());

    return variables.stream()
        .map(astVariable -> resolve(astVariable.toString(), scope))
        .filter(variableSymbol -> variableSymbol.getVectorParameter().isPresent())
        .findAny();
  }

  public static List<ASTReturnStmt> getReturnStatements(ASTBlock blockAst) {
    return AstUtils.getAll(blockAst, ASTReturnStmt.class);
  }

  public static List<String> getParameterTypes(final ASTFunctionCall astFunctionCall) {
    final List<String> argTypeNames = Lists.newArrayList();


    for (int i = 0; i < astFunctionCall.getArgs().size(); ++i) {
      final ASTExpr argExpr = astFunctionCall.getArgs().get(i);
      final Either<TypeSymbol, String> argType = argExpr.getType();
      if (argType.isValue()) {
        argTypeNames.add(argType.getValue().getName());
      }
      else {
        // TODO print the value of the expression
        throw new RuntimeException("Cannot determine the type of parameters in the function call at:  " + astFunctionCall.get_SourcePositionStart() + argType.getError());
      }

    }

    return argTypeNames;
  }

  public static String toString(final ASTExpr expr) {
    final ExpressionsPrettyPrinter printer = new ExpressionsPrettyPrinter();
    return printer.print(expr);
  }


  private static final class VariablesCollector implements NESTMLInheritanceVisitor {
    List<String> getVariableNames() {
      return Lists.newArrayList(variableNames);
    }

    final private Set<String> variableNames = Sets.newHashSet();

    @Override
    public void visit(final ASTVariable var) {
      variableNames.add(var.toString());

    }

  }

  /**
   * Returns all variable symbols for variables which are defined in the subtree starting from
   * the astNode.
   */
  public static List<VariableSymbol> getVariableSymbols(final ASTNESTMLNode astNode) {
    final VariableSymbolsCollector variableSymbolsCollector = new VariableSymbolsCollector();
    astNode.accept(variableSymbolsCollector);

    return variableSymbolsCollector.getVariables();
  }

  static private final class VariableSymbolsCollector implements NESTMLInheritanceVisitor {
    List<VariableSymbol> getVariables() {
      return Lists.newArrayList(variables);
    }

    final private Set<VariableSymbol> variables = Sets.newHashSet();

    @Override
    public void visit(final ASTVariable astVariable) {
      checkArgument(astVariable.getEnclosingScope().isPresent(), "Run symbol table creator.");
      final String variableName = astVariable.toString();
      final Scope scope = astVariable.getEnclosingScope().get();

      variables.add(resolve(variableName, scope));
    }

  }


  /**
   * Collects all neuron ASTs from every model root
   * @param modelRoots list with nestml roots
   * @return List with all neurons from roots.
   */
  public static List<ASTNeuron> getAllNeurons(final List<ASTNESTMLCompilationUnit> modelRoots) {
    return modelRoots.stream()
        .flatMap(root -> root.getNeurons().stream())
        .collect(Collectors.toList());
  }

  // TODO It works only with multiline comments
  public static String printComments(final ASTNode astNode) {
    final StringBuilder output = new StringBuilder();
    astNode.get_PreComments().forEach(comment -> output.append(comment.getText()));
    astNode.get_PostComments().forEach(comment -> output.append(comment.getText()));
    return output.toString();
  }


  /**
   * Computes the typename for the declaration ast. It is defined in one of the grammar
   * alternatives.
   */
  public static String computeTypeName(final ASTDatatype astDatatype) {
    String typeName = null;
    if (astDatatype.isBoolean()) {
      typeName = "boolean";
    }
    else if (astDatatype.isInteger()) {
      typeName = "integer";
    }
    else if (astDatatype.isReal()) {
      typeName = "real";
    }
    else if (astDatatype.isString()) {
      typeName = "string";
    }
    else if (astDatatype.isVoid()) {
      typeName = "void";
    }
    else if (astDatatype.getUnitType().isPresent()) {
      final ASTUnitType unitType = astDatatype.getUnitType().get();
      if(unitType.getSerializedUnit() == null){
        UnitsSIVisitor.convertSiUnitsToSignature(unitType);
      }
      typeName = unitType.getSerializedUnit();
    }
    else {
      checkState(false, "Is not possible through the grammar construction.");
    }

    return typeName;
  }

  public static Optional<ASTFunctionCall> getFunctionCall(final String functionName, final ASTNESTMLNode node) {
    final IntegrateFunctionCollector odeCollector = new IntegrateFunctionCollector(functionName);
    odeCollector.startVisitor(node);
    return odeCollector.getFunctionCall();
  }

  /**
   * Integrate function give the connection between a buffer and shape. Therefore, it is needed to generate
   * correct update with the PSCInitialValues.
   */
  private static class IntegrateFunctionCollector implements NESTMLInheritanceVisitor {
    private final String functionName;

    // Initialized by null and set to the actual value
    private ASTFunctionCall foundOde = null;

    private IntegrateFunctionCollector(String functionName) {
      this.functionName = functionName;
    }

    void startVisitor(ASTNESTMLNode node) {
      node.accept(this);
    }


    @Override
    public void visit(final ASTFunctionCall astFunctionCall) {
      // TODO actually works only for the first ode
      if (astFunctionCall.getCalleeName().equals(functionName)) {
        foundOde = astFunctionCall;
      }
    }

    Optional<ASTFunctionCall> getFunctionCall() {
      return Optional.ofNullable(foundOde);
    }

  }

  /**
   * If the variable is ues as a RHS of an equation, e.g. g_in'' = exp(t) then a variable g_in' should be added.
   *
   */
  public static String getNameOfLHS(final ASTDerivative astVariable) {
    checkArgument(astVariable.getDifferentialOrder().size() > 0);
    return astVariable.getName() + Strings.repeat("'", astVariable.getDifferentialOrder().size() - 1);
  }

  /**
   * Returns the name of the shape, 'shape g_in = t' -> g_n
   *
   */
  public static String getNameOfLHS(ASTShape astShape) {
    return astShape.getLhs().toString();
  }

  public static String convertDevrivativeNameToSimpleName(final ASTVariable astVariable) {
    if (astVariable.getDifferentialOrder().size() > 0) {
      return "__" + Strings.repeat("D", astVariable.getDifferentialOrder().size()) + astVariable.getName();

    }
    else {
      return astVariable.toString();
    }

  }



  public static boolean isInhExc(final ASTInputLine astInputLine ) {
    boolean isInh =false, isExc = false;
    for (final ASTInputType astInputType:astInputLine.getInputTypes()) {
      if (astInputType.isInhibitory()) {
        isInh = true;
      }

      if (astInputType.isExcitatory()) {
        isExc = true;
      }

    }
    return (!isInh && !isExc) || (isInh && isExc);
  }

  private static void printModelToFile(
      final ASTNeuron astNeuron,
      final Path outputFile) {
    final NESTMLPrettyPrinter prettyPrinter = NESTMLPrettyPrinter.Builder.build();
    astNeuron.accept(prettyPrinter);

    final File prettyPrintedModelFile = outputFile.toFile();
    try {
      FileUtils.write(prettyPrintedModelFile, prettyPrinter.result());
    }
    catch (IOException e) {
      final String msg = "Cannot write the prettyprinted model to the file: " + outputFile;
      throw new RuntimeException(msg, e);
    }

  }

  /**
   * Model is printed and read again due 2 reasons:
   * a) Technically it is necessary to build a new symbol table
   * b) The model developer can view how the solution was computed.
   * @return New root node of the altered model with an initialized symbol table
   */
  public static ASTNeuron deepCloneNeuronAndBuildSymbolTable(final ASTNeuron astNeuron, final Path temporaryFolder) {
    try {
      final Path outputTmpPath = Paths.get(temporaryFolder.toString(), astNeuron.getName() + ".tmp");

      printModelToFile(astNeuron, outputTmpPath);

      final ASTNESTMLCompilationUnit withSolvedOde = parser.parseNESTMLCompilationUnit(outputTmpPath.toString()).get();
      withSolvedOde.setArtifactName(astNeuron.getName());
      final NESTMLScopeCreator scopeCreator =  new NESTMLScopeCreator();
      scopeCreator.runSymbolTableCreator(withSolvedOde);
      return withSolvedOde.getNeurons().get(0);
    }
    catch (IOException e) {
      throw  new RuntimeException(e);
    }

  }

  public static String print(final SourcePosition sourcePosition) {
    return "<" + sourcePosition.getLine() + "," + sourcePosition.getColumn() + ">";
  }

  public static Optional<String> convertSiName(String astVariable) {
    for (String siUnit : SIData.getCorrectSIUnits()) {
      if (astVariable.equals(siUnit)) {
        TypeSymbol variableType = getType(astVariable);
        UnitRepresentation variableRep = UnitRepresentation
            .getBuilder()
            .serialization(variableType.getName())
            .build();
        int magnitude = UnitRepresentation.getTargetUnitFilter()
            .getDifferenceToRegisteredTarget(variableRep);
        double magnitudeAsFactor = pow(10.0, magnitude);
        return Optional.of(String.valueOf(magnitudeAsFactor));
      }
    }
    return Optional.empty();
  }

}
