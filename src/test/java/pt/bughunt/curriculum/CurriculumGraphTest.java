package pt.bughunt.curriculum;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CurriculumGraphTest {

    CurriculumGraph graph;

    @BeforeEach
    void buildSampleCurriculum() {
        graph = new CurriculumGraph();
        // Mini-currículo de exemplo:
        // variaveis → condicionais → ciclos → arrays → recursividade
        //                          ↘ funcoes ↗
        graph.addPrerequisite("condicionais", "variaveis");
        graph.addPrerequisite("ciclos", "condicionais");
        graph.addPrerequisite("funcoes", "condicionais");
        graph.addPrerequisite("arrays", "ciclos");
        graph.addPrerequisite("recursividade", "funcoes");
        graph.addPrerequisite("recursividade", "arrays");
    }

    @Test
    @DisplayName("Conceito sem pré-requisitos está sempre desbloqueado")
    void rootConceptAlwaysUnlocked() {
        assertTrue(graph.isUnlocked("variaveis", Set.of()));
    }

    @Test
    @DisplayName("Conceito bloqueado enquanto faltam pré-requisitos")
    void lockedUntilPrerequisitesMastered() {
        assertFalse(graph.isUnlocked("ciclos", Set.of("variaveis")));
        assertTrue(graph.isUnlocked("ciclos", Set.of("variaveis", "condicionais")));
    }

    @Test
    @DisplayName("Conceito com múltiplos pré-requisitos exige TODOS")
    void allPrerequisitesRequired() {
        // recursividade precisa de funcoes E arrays
        var partial = Set.of("variaveis", "condicionais", "ciclos", "funcoes");
        assertFalse(graph.isUnlocked("recursividade", partial));

        var complete = Set.of("variaveis", "condicionais", "ciclos", "funcoes", "arrays");
        assertTrue(graph.isUnlocked("recursividade", complete));
    }

    @Test
    @DisplayName("availableNext devolve só o que está desbloqueado e não dominado")
    void availableNextFiltersCorrectly() {
        var mastered = Set.of("variaveis", "condicionais");
        List<String> next = graph.availableNext(mastered);

        assertTrue(next.contains("ciclos"));
        assertTrue(next.contains("funcoes"));
        assertFalse(next.contains("variaveis"),   "já dominado");
        assertFalse(next.contains("arrays"),      "ainda bloqueado");
        assertFalse(next.contains("recursividade"), "ainda bloqueado");
    }

    @Test
    @DisplayName("Novo utilizador vê apenas os conceitos-raiz")
    void newUserSeesOnlyRoots() {
        assertEquals(List.of("variaveis"), graph.availableNext(Set.of()));
    }

    @Test
    @DisplayName("learningPathTo devolve caminho em ordem topológica")
    void learningPathIsTopologicallySorted() {
        List<String> path = graph.learningPathTo("recursividade");

        // O caminho tem de conter todos os pré-requisitos transitivos
        assertTrue(path.containsAll(List.of(
                "variaveis", "condicionais", "ciclos", "funcoes", "arrays", "recursividade")));

        // E cada pré-requisito tem de aparecer ANTES do conceito que o exige
        assertTrue(path.indexOf("variaveis") < path.indexOf("condicionais"));
        assertTrue(path.indexOf("condicionais") < path.indexOf("ciclos"));
        assertTrue(path.indexOf("ciclos") < path.indexOf("arrays"));
        assertTrue(path.indexOf("funcoes") < path.indexOf("recursividade"));
        assertTrue(path.indexOf("arrays") < path.indexOf("recursividade"));
        assertEquals("recursividade", path.get(path.size() - 1));
    }

    @Test
    @DisplayName("Criar um ciclo de pré-requisitos é rejeitado com rollback")
    void cycleDetectionWithRollback() {
        // variaveis ← condicionais já existe; fechar o ciclo é proibido
        assertThrows(IllegalStateException.class, () ->
                graph.addPrerequisite("variaveis", "recursividade"));

        // Rollback: o grafo continua utilizável e sem a aresta inválida
        assertTrue(graph.isUnlocked("variaveis", Set.of()));
    }

    @Test
    @DisplayName("Auto-dependência é rejeitada")
    void selfDependencyRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                graph.addPrerequisite("ciclos", "ciclos"));
    }

    @Test
    @DisplayName("Conceito desconhecido lança exceção clara")
    void unknownConceptThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                graph.isUnlocked("blockchain", Set.of()));
        assertThrows(IllegalArgumentException.class, () ->
                graph.learningPathTo("blockchain"));
    }
}
