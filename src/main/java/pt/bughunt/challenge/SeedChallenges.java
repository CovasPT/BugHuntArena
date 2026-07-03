package pt.bughunt.challenge;

import pt.bughunt.sandbox.ExecutionRequest.Language;

import java.util.List;
import java.util.Optional;

/**
 * Desafios seed para o protótipo — 8 desafios reais com bugs plantados,
 * cobrindo fundamentos e segurança. Cada bug é de um TIPO diferente
 * para alimentar o ErrorPatternTracker com conceitos distintos.
 *
 * Na versão final estes vivem na tabela `challenges` da BD;
 * aqui em código para o protótipo funcionar sem BD.
 */
public final class SeedChallenges {

    private SeedChallenges() {}

    public static final List<Challenge> ALL = List.of(

            // ---------- Nível 1: fundamentos ----------

            new Challenge("off-by-one-01", "ciclos",
                    "O array rebelde",
                    "Este programa devia imprimir a soma dos elementos do array, "
                    + "mas rebenta com uma exceção. Encontra e corrige o bug.",
                    Language.JAVA,
                    """
                    public class Main {
                        public static void main(String[] args) {
                            int[] numeros = {10, 20, 30, 40, 50};
                            int soma = 0;
                            for (int i = 0; i <= numeros.length; i++) {
                                soma += numeros[i];
                            }
                            System.out.println("Soma: " + soma);
                        }
                    }
                    """,
                    "Soma: 150",
                    1, 100),

            new Challenge("string-compare-01", "strings",
                    "Iguais mas diferentes",
                    "O login devia aceitar a password correta, mas rejeita sempre. "
                    + "Porquê? (Dica: em Java, nem tudo o que parece igual É igual.)",
                    Language.JAVA,
                    """
                    public class Main {
                        public static void main(String[] args) {
                            String passwordCorreta = new String("bughunt2026");
                            String tentativa = new String("bughunt2026");
                            if (passwordCorreta == tentativa) {
                                System.out.println("Acesso concedido");
                            } else {
                                System.out.println("Acesso negado");
                            }
                        }
                    }
                    """,
                    "Acesso concedido",
                    1, 100),

            new Challenge("mutable-default-01", "funcoes",
                    "A lista fantasma",
                    "Cada chamada devia criar uma lista nova, mas os itens "
                    + "acumulam-se entre chamadas. Um dos bugs mais famosos de Python.",
                    Language.PYTHON,
                    """
                    def adicionar_item(item, lista=[]):
                        lista.append(item)
                        return lista

                    print(adicionar_item("a"))
                    print(adicionar_item("b"))
                    print(adicionar_item("c"))
                    """,
                    "['a']\n['b']\n['c']",
                    2, 150),

            // ---------- Nível 2: lógica ----------

            new Challenge("integer-division-01", "tipos",
                    "A média impossível",
                    "A média de 7 e 8 devia ser 7.5, mas o programa insiste em 7.0. "
                    + "O bug está na ordem das operações... ou será nos tipos?",
                    Language.JAVA,
                    """
                    public class Main {
                        public static void main(String[] args) {
                            int nota1 = 7;
                            int nota2 = 8;
                            double media = nota1 / nota2 * 2 + nota1 / 2;
                            media = (nota1 + nota2) / 2;
                            System.out.println("Media: " + media);
                        }
                    }
                    """,
                    "Media: 7.5",
                    2, 150),

            new Challenge("closure-loop-01", "closures",
                    "Todos ao mesmo tempo",
                    "Devia imprimir 0, 1, 2 — mas imprime 3, 3, 3. "
                    + "Um clássico de JavaScript sobre scoping.",
                    Language.JAVASCRIPT,
                    """
                    const funcoes = [];
                    for (var i = 0; i < 3; i++) {
                        funcoes.push(function() { console.log(i); });
                    }
                    funcoes.forEach(f => f());
                    """,
                    "0\n1\n2",
                    2, 150),

            // ---------- Nível 3: segurança (Secure by Design) ----------

            new Challenge("sql-injection-01", "seguranca-input",
                    "A pesquisa perigosa",
                    "Esta função constrói uma query SQL por concatenação — vulnerável "
                    + "a SQL injection. Reescreve-a para simular um prepared statement: "
                    + "a função sanitize() deve neutralizar aspas simples duplicando-as, "
                    + "como fazem os drivers reais.",
                    Language.PYTHON,
                    """
                    def sanitize(valor):
                        # TODO: neutralizar aspas simples
                        return valor

                    def build_query(username):
                        return "SELECT * FROM users WHERE name = '" + sanitize(username) + "'"

                    # Simulação de ataque:
                    print(build_query("maria"))
                    print(build_query("x' OR '1'='1"))
                    """,
                    "SELECT * FROM users WHERE name = 'maria'\n"
                    + "SELECT * FROM users WHERE name = 'x'' OR ''1''=''1'",
                    3, 250),

            new Challenge("hardcoded-secret-01", "seguranca-segredos",
                    "O segredo mal guardado",
                    "Este código tem uma API key hardcoded — má prática grave. "
                    + "Altera-o para ler a key da variável de ambiente API_KEY, "
                    + "e imprimir 'ERRO: API_KEY nao definida' se não existir. "
                    + "(No sandbox a variável não existe, por isso o output esperado é o erro.)",
                    Language.PYTHON,
                    """
                    API_KEY = "sk-prod-9f8e7d6c5b4a"

                    def get_key():
                        return API_KEY

                    key = get_key()
                    print("A usar key: " + key)
                    """,
                    "ERRO: API_KEY nao definida",
                    3, 250),

            new Challenge("race-condition-01", "concorrencia",
                    "O contador desonesto",
                    "Dois threads incrementam o mesmo contador 100.000 vezes cada, "
                    + "mas o total quase nunca dá 200.000. Torna o incremento atómico.",
                    Language.JAVA,
                    """
                    public class Main {
                        static int contador = 0;

                        public static void main(String[] args) throws InterruptedException {
                            Runnable tarefa = () -> {
                                for (int i = 0; i < 100_000; i++) {
                                    contador++;
                                }
                            };
                            Thread t1 = new Thread(tarefa);
                            Thread t2 = new Thread(tarefa);
                            t1.start(); t2.start();
                            t1.join(); t2.join();
                            System.out.println("Total: " + contador);
                        }
                    }
                    """,
                    "Total: 200000",
                    4, 400)
    );

    public static Optional<Challenge> byId(String id) {
        return ALL.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public static List<Challenge> byConcept(String conceptId) {
        return ALL.stream().filter(c -> c.conceptId().equals(conceptId)).toList();
    }

    public static List<Challenge> byMaxDifficulty(int maxDifficulty) {
        return ALL.stream().filter(c -> c.difficulty() <= maxDifficulty).toList();
    }
}
