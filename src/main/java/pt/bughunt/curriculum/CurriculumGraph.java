package pt.bughunt.curriculum;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Grafo de conceitos com pré-requisitos — o coração do modelo "Duolingo".
 *
 * Cada conceito (ex: "ciclos-for") pode depender de outros
 * (ex: "variaveis", "condicionais"). Um conceito só fica
 * desbloqueado quando TODOS os pré-requisitos estão dominados.
 *
 * Internamente é um DAG (grafo dirigido acíclico) — a validação
 * de ciclos usa DFS com deteção de back-edges, exatamente o tipo
 * de algoritmo que estudaste em Arquitetura da Internet (Dijkstra
 * é primo disto).
 */
public class CurriculumGraph {

    /** conceito → conjunto de pré-requisitos diretos */
    private final Map<String, Set<String>> prerequisites = new HashMap<>();

    public void addConcept(String conceptId) {
        prerequisites.computeIfAbsent(conceptId, k -> new HashSet<>());
    }

    /**
     * Declara que {@code concept} exige {@code prerequisite} primeiro.
     * Lança IllegalStateException se a aresta criasse um ciclo
     * (ex: A precisa de B, B precisa de A — currículo impossível).
     */
    public void addPrerequisite(String concept, String prerequisite) {
        if (concept.equals(prerequisite)) {
            throw new IllegalArgumentException(
                    "Um conceito não pode ser pré-requisito de si próprio: " + concept);
        }
        addConcept(concept);
        addConcept(prerequisite);
        prerequisites.get(concept).add(prerequisite);

        if (hasCycle()) {
            prerequisites.get(concept).remove(prerequisite); // rollback
            throw new IllegalStateException(
                    "Adicionar '%s' como pré-requisito de '%s' criaria um ciclo"
                            .formatted(prerequisite, concept));
        }
    }

    /**
     * Um conceito está desbloqueado se todos os seus pré-requisitos
     * diretos estiverem no conjunto de conceitos dominados.
     */
    public boolean isUnlocked(String concept, Set<String> mastered) {
        Set<String> required = prerequisites.get(concept);
        if (required == null) {
            throw new IllegalArgumentException("Conceito desconhecido: " + concept);
        }
        return mastered.containsAll(required);
    }

    /**
     * Devolve os conceitos que o utilizador pode estudar a seguir:
     * desbloqueados mas ainda não dominados. É isto que alimenta
     * o ecrã principal da app ("as tuas próximas lições").
     */
    public List<String> availableNext(Set<String> mastered) {
        return prerequisites.keySet().stream()
                .filter(c -> !mastered.contains(c))
                .filter(c -> isUnlocked(c, mastered))
                .sorted()
                .toList();
    }

    /**
     * Caminho de aprendizagem completo até um conceito-alvo,
     * em ordem topológica (pré-requisitos primeiro).
     * Ex: learningPathTo("recursividade") →
     *     [variaveis, funcoes, stack, recursividade]
     */
    public List<String> learningPathTo(String target) {
        if (!prerequisites.containsKey(target)) {
            throw new IllegalArgumentException("Conceito desconhecido: " + target);
        }
        // DFS pós-ordem: visita pré-requisitos antes do próprio conceito
        Set<String> visited = new HashSet<>();
        Deque<String> result = new ArrayDeque<>();
        dfsPath(target, visited, result);
        return List.copyOf(result);
    }

    private void dfsPath(String node, Set<String> visited, Deque<String> result) {
        if (visited.contains(node)) return;
        visited.add(node);
        for (String prereq : prerequisites.get(node)) {
            dfsPath(prereq, visited, result);
        }
        result.addLast(node);
    }

    /** Deteção de ciclos por DFS com três estados (branco/cinzento/preto). */
    private boolean hasCycle() {
        Set<String> visiting = new HashSet<>();
        Set<String> done = new HashSet<>();
        for (String node : prerequisites.keySet()) {
            if (dfsCycle(node, visiting, done)) return true;
        }
        return false;
    }

    private boolean dfsCycle(String node, Set<String> visiting, Set<String> done) {
        if (done.contains(node)) return false;
        if (visiting.contains(node)) return true; // back-edge → ciclo
        visiting.add(node);
        for (String prereq : prerequisites.get(node)) {
            if (dfsCycle(prereq, visiting, done)) return true;
        }
        visiting.remove(node);
        done.add(node);
        return false;
    }

    public Set<String> allConcepts() {
        return Set.copyOf(prerequisites.keySet());
    }
}
