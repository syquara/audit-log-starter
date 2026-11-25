# audit-log-starter

Biblioteca Spring Boot para registrar eventos de auditoria de métodos de negócio por meio da anotação `@AuditLog`. O starter intercepta a execução do método, coleta informações da operação e publica um registro estruturado em JSON no log da aplicação.

> **Versão documentada:** `0.2.0`  
> **GroupId:** `com.siquaras`  
> **ArtifactId:** `audit-log-starter`

## 1. Visão geral

O `audit-log-starter` permite adicionar auditoria a métodos específicos sem implementar manualmente a coleta de informações em cada serviço.

Atualmente, a biblioteca:

- Intercepta métodos anotados com `@AuditLog`, utilizando Spring AOP.
- Registra a ação e o domínio de negócio informados na anotação.
- Registra a classe, o método e o tempo de execução.
- Pode coletar os parâmetros de entrada e o retorno do método.
- Registra sucesso ou falha da execução.
- Tenta identificar o usuário autenticado por meio do contexto do Spring Security, quando disponível.
- Coleta o endereço remoto da requisição e o identificador de requisição.
- Aplica mascaramento a campos e valores reconhecidos como sensíveis.
- Publica o evento como JSON no logger SLF4J da aplicação.

### Fluxo simplificado

1. A aplicação executa um método anotado com `@AuditLog`.
2. O aspecto `AuditLogAspect` intercepta a chamada.
3. O método de negócio é executado.
4. Em caso de sucesso, os dados permitidos pela anotação são coletados e publicados.
5. Em caso de exceção (`Exception`), é publicado um registro de falha e a exceção é relançada.

## 2. Requisitos do sistema

Requisitos identificados no `pom.xml` desta versão:

| Componente | Requisito |
|---|---|
| Java | 21 ou superior |
| Maven | Maven 3.6.3 ou superior recomendado |
| Spring Boot | Linha 3.x; dependências do starter fixadas em 3.5.16 |
| Spring Web | Necessário |
| Spring AOP | Necessário |
| Jackson Databind 2 | Necessário (`com.fasterxml.jackson.databind.ObjectMapper`) |
| Lombok | Utilizado na compilação da biblioteca |

### Compatibilidade importante

Esta versão utiliza `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2) e dependências Spring Boot 3.5.16. **Não presuma compatibilidade com Spring Boot 4.x**, que utiliza a geração Jackson 3 nas configurações padrão. A compatibilidade com outras versões do Spring Boot deve ser validada antes da adoção.

A biblioteca foi estruturada para aplicações Spring Boot web/servlet. O código de coleta de contexto HTTP utiliza `HttpServletRequest`.

## 3. Instalação

A biblioteca pode ser consumida de duas formas: por um repositório Maven corporativo compartilhado ou por um repositório Maven baseado em arquivo (`libs-repo`) mantido dentro do projeto consumidor. Para desenvolvimento local do próprio starter, também é possível utilizar `mvn install`.

### 3.1. Publicação/instalação da biblioteca

Na raiz do projeto `audit-log-starter`, execute:

```bash
mvn clean install
```

Esse comando compila e instala o artefato no repositório Maven local do desenvolvedor. Ele **não publica** automaticamente a biblioteca no repositório remoto corporativo.

Para disponibilizar o artefato no repositório remoto, a equipe responsável deve configurar o `distributionManagement` no `pom.xml` ou utilizar o mecanismo de publicação definido pela organização. Os endereços e credenciais do repositório não estão definidos neste projeto e não devem ser inventados.

Exemplo de configuração Maven, a ser ajustado com os dados reais do repositório corporativo:

```xml
<distributionManagement>
    <repository>
        <id>gis-maven-releases</id>
        <url>URL_DO_REPOSITORIO_MAVEN_RELEASES</url>
    </repository>
    <snapshotRepository>
        <id>gis-maven-snapshots</id>
        <url>URL_DO_REPOSITORIO_MAVEN_SNAPSHOTS</url>
    </snapshotRepository>
</distributionManagement>
```

As credenciais correspondentes devem ser configuradas no `settings.xml` do Maven, associadas aos mesmos valores de `<id>`. Não versionar usuário, senha ou token no `pom.xml`.

### 3.2. Opção A — consumir pelo repositório Maven corporativo

Quando o artefato estiver publicado no repositório Maven corporativo, configure o acesso ao repositório conforme o padrão da organização e adicione a dependência no `pom.xml` da aplicação:

```xml
<dependency>
    <groupId>com.siquaras</groupId>
    <artifactId>audit-log-starter</artifactId>
    <version>0.2.0</version>
</dependency>
```

Utilize a versão efetivamente publicada no repositório. Essa é a opção mais adequada quando vários projetos e pipelines precisam consumir a mesma biblioteca.

### 3.3. Opção B — manter um repositório Maven local dentro do projeto consumidor (`libs-repo`)

Também é possível distribuir o JAR junto ao próprio projeto consumidor, sem depender de uma publicação prévia no repositório Maven corporativo. Essa alternativa está descrita no documento de instalação fornecido.

**Passo 1 — criar a pasta e adicionar o JAR**

Na raiz do módulo/projeto consumidor, crie a pasta `libs-repo` e copie para ela o JAR da versão desejada. Exemplo:

```text
meu-projeto/
├── pom.xml
├── libs-repo/
│   └── audit-log-starter-0.2.0.jar
└── src/
    └── main/
        └── resources/
            └── sensitive_fields.txt
```

**Passo 2 — declarar o repositório no `pom.xml`**

Adicione o repositório local no `pom.xml` do módulo que consumirá a biblioteca:

```xml
<repositories>
    <repository>
        <id>local-libs-repo</id>
        <url>file://${project.basedir}/libs-repo</url>
    </repository>
</repositories>
```

Mantenha também a dependência:

```xml
<dependency>
    <groupId>com.siquaras</groupId>
    <artifactId>audit-log-starter</artifactId>
    <version>0.2.0</version>
</dependency>
```

**Passo 3 — instalar o JAR na estrutura Maven dentro de `libs-repo`**

Execute o comando abaixo, ajustando a versão e os caminhos para a estrutura real do seu projeto:

```bash
mvn deploy:deploy-file \
  -DgroupId=com.siquaras \
  -DartifactId=audit-log-starter \
  -Dversion=0.2.0 \
  -Dpackaging=jar \
  -Dfile=./libs-repo/audit-log-starter-0.2.0.jar \
  -Durl=file:./libs-repo \
  -DrepositoryId=local-libs-repo
```

No Windows, o comando pode ser executado em uma única linha:

```bat
mvn deploy:deploy-file -DgroupId=com.siquaras -DartifactId=audit-log-starter -Dversion=0.2.0 -Dpackaging=jar -Dfile=./libs-repo/audit-log-starter-0.2.0.jar -Durl=file:./libs-repo -DrepositoryId=local-libs-repo
```

Atenção aos parâmetros:

| Parâmetro | O que ajustar |
|---|---|
| `-Dversion` | Versão do JAR que será disponibilizado. |
| `-Dfile` | Caminho do arquivo JAR copiado para o projeto. |
| `-Durl` | Caminho da pasta `libs-repo` que será utilizada como repositório Maven. |
| `-DgroupId` | Deve corresponder ao `groupId` da biblioteca. |
| `-DartifactId` | Deve corresponder ao `artifactId` da biblioteca. |

O comando gera dentro de `libs-repo` a estrutura de diretórios e os metadados esperados pelo Maven. Depois disso, o Maven poderá resolver a dependência declarada no `pom.xml` por meio do repositório baseado em arquivo.

Execute esse procedimento novamente quando receber uma nova versão do JAR. A pasta `libs-repo` deve ser mantida junto ao projeto se a intenção for permitir que outros desenvolvedores e o pipeline de build consumam a biblioteca dessa mesma forma. Verifique também as regras internas do GIS sobre versionamento e armazenamento de binários no repositório Git.

> **Importante:** o exemplo do documento de instalação usa caminhos específicos do projeto de demonstração. Não copie esses caminhos literalmente. No exemplo acima, considera-se que o comando é executado na raiz do módulo consumidor e que o JAR está diretamente em `./libs-repo/`.

### 3.4. Qual opção utilizar?

| Cenário | Opção recomendada |
|---|---|
| Vários projetos precisam compartilhar a biblioteca | Repositório Maven corporativo |
| Projeto precisa carregar a biblioteca localmente junto ao código | Repositório Maven baseado em `libs-repo` |
| Desenvolvimento local/teste do próprio starter | `mvn clean install` no projeto da biblioteca |

O `mvn clean install` instala o artefato no repositório Maven local do desenvolvedor (`.m2`). Ele é diferente da opção `libs-repo`: não cria nem atualiza o repositório local que fica dentro do projeto consumidor.

### 3.5. Dependências providas pelo starter

O `pom.xml` do starter declara:

- `spring-boot-starter-web` — versão 3.5.16.
- `spring-boot-starter-aop` — versão 3.5.16.
- `jackson-databind` — versão gerenciada pelo BOM do Spring Boot 3.5.16.
- `h2` — versão 2.5.252, usado pela captura temporária de logs.
- `lombok` — versão 1.18.48, com escopo `provided`.

Avalie o gerenciamento de versões do projeto consumidor para evitar conflitos de dependências, especialmente se ele utilizar um BOM ou parent corporativo.

## 4. Configuração

### 4.1. Autoconfiguração

A biblioteca registra `AuditAutoConfiguration` em:

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

O recurso de captura temporária de logs e exceções também é ativado automaticamente por `ApiExceptionLoggingConfiguration` em aplicações Servlet.

Assim, em uma aplicação Spring Boot compatível, a configuração é carregada automaticamente quando o starter está no classpath. Ela registra:

- `AuditPublisher`, com implementação `DefaultAuditPublisher`;
- `AuditLogAspect`, responsável pela interceptação;
- `LoadSensitiveFields`, responsável pela leitura dos campos sensíveis.

Não é necessário adicionar `@ComponentScan` para o pacote interno da biblioteca.

### 4.2. Arquivo de campos sensíveis

Crie o arquivo abaixo no classpath da aplicação consumidora:

```text
src/main/resources/sensitive_fields.txt
```

Informe um campo por linha. A biblioteca normaliza os nomes antes de compará-los: remove acentos, espaços, `_` e `-`, e converte para minúsculas.

Exemplo:

```text
password
senha
token
authorization
creditCard
numeroCartao
```

O arquivo é carregado durante a inicialização da aplicação. Se não existir, a biblioteca registra uma mensagem de erro no log; a inicialização do bean não é interrompida por essa falha de leitura, mas os campos personalizados desse arquivo não serão protegidos.

A comparação procura o texto normalizado configurado dentro do nome do campo. Por isso, uma entrada como `password` também pode corresponder a nomes como `userPassword`.

### 4.3. Propriedades

A versão `0.2.0` não declara propriedades externas próprias (`@ConfigurationProperties`) para habilitar/desabilitar a auditoria ou configurar destino, nível de log, formato ou comportamento de mascaramento.

A seleção das operações auditadas é feita diretamente pela anotação `@AuditLog`.

## 5. Como utilizar

### 5.1. Anotar o método que deve ser auditado

Importe:

```java
import com.siquaras.auditlog.annotation.AuditLog;
```

Exemplo:

```java
@Service
public class CustomerService {

    @AuditLog(
        action = "CREATE_CUSTOMER",
        domain = "CUSTOMER"
    )
    public Customer createCustomer(CustomerRequest request) {
        // Implementação da regra de negócio
        return customerRepository.save(
            new Customer(request.name(), request.email())
        );
    }
}
```

A anotação deve ser aplicada a métodos. O método precisa ser chamado por meio do proxy Spring para que a interceptação AOP ocorra. Chamadas internas do mesmo objeto (self-invocation) não passam pelo proxy padrão do Spring AOP e, portanto, não devem ser consideradas auditadas.

### 5.2. Parâmetros da anotação

| Parâmetro | Obrigatório | Padrão | Descrição |
|---|---:|---|---|
| `action` | Sim | — | Identificador da ação de negócio. Ex.: `CREATE_CUSTOMER`. |
| `domain` | Não | `""` | Domínio/contexto de negócio. Ex.: `CUSTOMER`. |
| `logRequest` | Não | `true` | Define se os parâmetros de entrada serão incluídos. |
| `logResponse` | Não | `true` | Define se o retorno será incluído. |

Exemplo sem registrar entrada e retorno:

```java
@AuditLog(
    action = "DELETE_CUSTOMER",
    domain = "CUSTOMER",
    logRequest = false,
    logResponse = false
)
public void deleteCustomer(Long customerId) {
    // ...
}
```

Mesmo com `logRequest = false` ou `logResponse = false`, os metadados da operação e o resultado (sucesso/falha) continuam sendo publicados.

## 6. Conteúdo do evento de auditoria

O objeto `AuditData` contém os seguintes campos:

| Campo | Descrição |
|---|---|
| `action` | Ação informada em `@AuditLog`. |
| `domain` | Domínio informado em `@AuditLog`. |
| `classe` | Nome completo da classe do objeto alvo. |
| `metodo` | Nome do método interceptado. |
| `request` | Dados de entrada, quando habilitados. |
| `response` | Dados de retorno, quando habilitados e quando a execução termina com sucesso. |
| `tempoExecucao` | Tempo de execução em milissegundos. |
| `sucesso` | `true` quando o método termina normalmente; `false` quando ocorre `Exception`. |
| `erro` | Mensagem da exceção em caso de falha. |

### 6.1. Dados de requisição

Quando `logRequest = true`, o registro de requisição contém:

- `authentication.userId`: identificador de usuário obtido do principal JWT, quando disponível.
- `authentication.userName`: nome obtido da autenticação.
- `attributes.requestId`: identificador obtido da requisição HTTP.
- `attributes.remoteAddr`: endereço remoto da requisição.
- `body`: mapa com os nomes e valores dos parâmetros do método, após sanitização.

Quando não houver contexto HTTP ou autenticação, os campos de identificação podem receber `UNKNOWN`.

**Observação:** nesta versão, o objeto de requisição possui um campo `headers`, mas o aspecto não o preenche. Não considere os headers HTTP como parte do evento coletado atualmente.

### 6.2. Identificação do usuário

A resolução de autenticação é feita por reflexão, tentando acessar `SecurityContextHolder` do Spring Security. A biblioteca não declara `spring-boot-starter-security` como dependência obrigatória.

Quando o principal disponibiliza `getClaimAsString`, a biblioteca tenta obter os claims:

- `userid`
- `user_name`

Se a aplicação utilizar nomes de claims diferentes, esta versão não oferece configuração para alterá-los. Sem Spring Security, sem autenticação válida ou sem os claims esperados, os valores podem permanecer como `UNKNOWN` ou usar o nome disponível na autenticação.

### 6.3. Dados de resposta

Quando `logResponse = true`, o retorno é inspecionado para coletar seus campos.

Se o método retornar `ResponseEntity`, o evento também inclui:

- `headers`;
- `statusCode`;
- `statusCodeValue`;
- `body` (corpo da resposta).

Para retornos que não sejam `ResponseEntity`, o código coleta os campos declarados no objeto retornado; os campos de status e headers ficam sem valor.

O inspetor atual percorre campos declarados diretamente na classe do objeto. Não há garantia de inclusão de campos herdados.

## 7. Mascaramento de dados sensíveis

A sanitização é realizada por `AuditObjectInspector` e `AuditMaskEngine`.

A implementação aplica mascaramento por nome de campo e/ou por padrão reconhecido no valor. Entre os casos tratados estão:

| Tipo | Exemplo de saída mascarada |
|---|---|
| Campo configurado em `sensitive_fields.txt` | `***` seguido dos últimos 4 caracteres (ou `***` para valores curtos) |
| CPF com 11 dígitos | `*********` + 2 últimos dígitos |
| CNPJ com 14 dígitos | `*********` + 2 últimos dígitos |
| E-mail | primeiro caractere + `***` + domínio |
| Telefone com 10 ou 11 dígitos | `*******` + 4 últimos dígitos |
| JWT reconhecido | `***JWT***` |
| Valor iniciado por `Bearer ` | `***TOKEN***` |

O inspetor também percorre coleções, mapas e campos de objetos aninhados.

> **Atenção:** mascaramento automático não substitui uma política de proteção de dados. Revise os DTOs e os dados efetivamente enviados aos logs. Não inclua senhas, tokens, segredos, dados financeiros ou dados pessoais desnecessários nos parâmetros/retornos auditados. Teste os padrões reais da aplicação antes de utilizar os registros em produção.

## 8. Publicação dos eventos

A implementação padrão (`DefaultAuditPublisher`) serializa `AuditData` com Jackson e escreve uma linha no logger SLF4J com o prefixo:

```text
AUDIT=
```

Exemplo ilustrativo de estrutura (os valores dependem da aplicação):

```json
{
  "action": "CREATE_CUSTOMER",
  "domain": "CUSTOMER",
  "classe": "com.example.CustomerService",
  "metodo": "createCustomer",
  "tempoExecucao": 18,
  "sucesso": true
}
```

O destino final depende da configuração de logging da aplicação consumidora (console, arquivo ou agregador de logs).

A interface `AuditPublisher` permite substituir a implementação padrão por um bean próprio, caso a aplicação precise encaminhar os eventos para outro destino. A implementação de integração com sistemas externos não faz parte desta versão.

### 8.1. Captura temporária de logs e exceções

A auto-configuração disponibiliza uma sessão H2 em memória, iniciada manualmente e desligada por padrão. A integração de captura de logs desta versão usa Logback; o endpoint de status informa `logCaptureAvailable` e `loggingBackend`.

Inicie a captura por 120 segundos (duração padrão) ou informe uma duração de 1 a 3600 segundos:

```http
PUT /auditLog/exceptionCapture
Content-Type: application/json

{
  "enabled": true,
  "levels": ["INFO", "ERROR"],
  "durationSeconds": 120
}
```

Os níveis aceitos são `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` e `ALL`. A lista de níveis deve conter ao menos um valor. Consulte o estado da sessão com `GET /auditLog/exceptionCapture` e os registros com `GET /auditLog/exceptionCapture/events?limit=100` (limite de 1 a 1000). Envie `{"enabled": false}` ao `PUT` para encerrar antes do prazo.

Cada registro contém o horário de captura e do log, nível, logger, thread, classe e método de origem, mensagem e, quando disponível, tipo, mensagem e stack trace da exceção. A origem é obtida do stack trace da exceção ou do caller data do Logback; os eventos observados pelo resolver MVC também usam a classe/método do handler quando a pilha não identifica código da aplicação. Esses campos aparecem como `sourceClass` e `sourceMethod`. Exceções observadas pelo resolver MVC também carregam metadados da requisição. `eventJson` é preenchido quando a mensagem inteira é um objeto ou array JSON; mensagens prefixadas como `AUDIT=...` permanecem disponíveis no campo `message`.

A fila de gravação aceita até 256 eventos pendentes; eventos recusados são contabilizados em `droppedEvents`. O texto da mensagem é limitado a 32 KiB UTF-8, a mensagem da exceção a 8 KiB e o stack trace a 64 KiB. A sessão retém no máximo 10000 registros e 32 MiB estimados, removendo os mais antigos para manter os mais recentes. Ao parar ou expirar, o banco em memória é encerrado e os dados deixam de estar disponíveis.

#### Endpoints disponíveis

As rotas usam `camelCase` e são registradas automaticamente em aplicações Spring MVC/Servlet. A captura começa desabilitada. Proteja estes endpoints com a política de autenticação/autorização da aplicação consumidora.

| Método | Endpoint | Função |
|---|---|---|
| `GET` | `/auditLog/exceptionCapture` | Consulta o estado da captura e seus contadores. |
| `PUT` | `/auditLog/exceptionCapture` | Inicia ou encerra a captura temporária. |
| `GET` | `/auditLog/exceptionCapture/events?limit=100` | Consulta os eventos mais recentes; `limit` aceita valores de 1 a 1000 e, por padrão, é 100. |

Exemplo de resposta do `GET /auditLog/exceptionCapture` e do `PUT`:

```json
{
  "enabled": true,
  "levels": ["INFO", "ERROR"],
  "expiresAt": "2026-11-24T15:30:00Z",
  "storedCount": 8,
  "storedBytes": 4096,
  "droppedEvents": 0,
  "writeFailures": 0,
  "logCaptureAvailable": true,
  "loggingBackend": "logback"
}
```

O `PUT` exige `enabled`. Para iniciar, envie também pelo menos um nível aceito (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` ou `ALL`). `durationSeconds` é opcional; quando omitido, vale 120 segundos. O intervalo permitido é de 1 a 3600 segundos. Para encerrar imediatamente, envie:

```json
{
  "enabled": false
}
```

O `GET .../events` retorna uma lista ordenada do evento mais recente para o mais antigo. Cada item inclui `id`, `capturedAt`, `eventType`, `level`, `loggerName`, `threadName`, `message`, `exceptionType`, `exceptionMessage`, `stackTrace`, `requestMethod`, `requestPath`, `requestId`, `handler`, `sourceClass`, `sourceMethod` e `eventJson`. Campos sem informação disponível podem ser `null`. `eventJson` só é preenchido quando a mensagem inteira é um objeto ou array JSON.

Respostas de erro: `400 Bad Request` para payload ou limite inválido; `503 Service Unavailable` quando a captura não pode ser iniciada/consultada. Ao parar ou expirar, a base H2 temporária é encerrada e sua lista de eventos volta vazia.

### 8.2. Substituição do publisher

A aplicação consumidora pode fornecer seu próprio bean `AuditPublisher`; nesse caso, o publisher padrão recua. Falhas de publicação ou sanitização são registradas sem substituir o retorno ou a exceção original do método de negócio.

## 9. Tratamento de erros e comportamento

- Se o método auditado lançar uma `Exception`, o aspecto publica um evento com `sucesso = false`, a mensagem da exceção e o tempo de execução; em seguida, relança a exceção.
- Se a serialização do evento falhar no `DefaultAuditPublisher`, o erro é registrado no logger e não é propagado pelo publisher.
- A resolução de autenticação trata falhas de leitura como não impeditivas e retorna os valores disponíveis/padrão.
- A falha ao carregar `sensitive_fields.txt` é registrada no log, mas não interrompe por si só a criação do bean.

O aspecto registra falhas de negócio (incluindo `Error`) e relança a mesma falha. Falhas comuns durante a construção ou publicação do evento de auditoria são registradas sem alterar o resultado do método de negócio. Erros fatais da JVM ainda podem interromper a aplicação.

## 10. Boas práticas de adoção

1. Utilize nomes estáveis e padronizados para `action` e `domain`.
2. Audite operações relevantes de negócio, evitando anotar indiscriminadamente todos os métodos.
3. Desabilite `logRequest` e/ou `logResponse` quando o conteúdo não for necessário.
4. Mantenha `sensitive_fields.txt` versionado junto à aplicação consumidora e revise-o quando novos DTOs forem criados.
5. Teste o mascaramento com dados representativos, incluindo objetos aninhados e coleções.
6. Configure retenção, acesso e monitoramento dos logs conforme as políticas de segurança da organização.
7. Evite registrar dados pessoais ou credenciais sem necessidade e sem base apropriada.
8. Verifique o comportamento em métodos chamados por outros beans Spring; chamadas internas na mesma classe não são interceptadas pelo proxy padrão.
9. Valide a compatibilidade das versões de Spring Boot, Spring Framework e Jackson antes de atualizar dependências.

## 11. Solução de problemas

### O evento não aparece no log

- Confirme que o método está anotado com `@AuditLog`.
- Confirme que a chamada passa pelo proxy Spring (não é uma chamada interna `this.metodo()`).
- Verifique se a autoconfiguração foi carregada e se o starter está no classpath.
- Verifique a configuração de nível e destino do logging da aplicação.

### Erro de dependência `ObjectMapper`

Esta versão exige `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2). Verifique se `jackson-databind` está disponível e se não há conflito com a versão gerenciada pela aplicação. Aplicações Spring Boot 4/Jackson 3 não são compatíveis sem adaptação da biblioteca.

### Campos sensíveis não são mascarados

- Confirme que `sensitive_fields.txt` está em `src/main/resources` da aplicação consumidora.
- Confirme que o nome do campo está no arquivo e em uma linha separada.
- Confira se o valor está sendo inspecionado pelo mecanismo de sanitização.
- Lembre-se de que os padrões automáticos de CPF/CNPJ/telefone consideram os formatos de dígitos descritos nesta documentação.

### Usuário aparece como `UNKNOWN`

- Verifique se a aplicação utiliza Spring Security e se há autenticação no contexto da thread.
- Para extração de ID/nome do JWT, confira os claims `userid` e `user_name`.
- Para outros tipos de principal ou nomes de claims, será necessária uma adaptação.

### Parâmetros aparecem sem o nome esperado

O aspecto utiliza os nomes dos parâmetros obtidos por reflexão (`MethodSignature.getParameterNames()`). Garanta que a compilação da aplicação preserve nomes de parâmetros (opção Java `-parameters`) e valide o resultado no ambiente de build.

## 12. Estrutura do projeto

```text
audit-log-starter/
├── pom.xml
└── src/
    └── main/
        ├── java/com/siquaras/auditlog/
        │   ├── annotation/
        │   │   └── AuditLog.java
        │   ├── aspect/
        │   │   └── AuditLogAspect.java
        │   ├── configuration/
        │   │   ├── AuditAutoConfiguration.java
        │   │   ├── ApiExceptionLoggingConfiguration.java
        │   │   └── LoadSensitiveFields.java
        │   ├── controller/
        │   │   └── ApiExceptionCaptureController.java
        │   ├── dto/
        │   │   ├── CaptureRequest.java
        │   │   ├── CaptureStatus.java
        │   │   └── CapturedApiLogRecord.java
        │   ├── logging/
        │   │   ├── ApiErrorLogCaptureAppender.java
        │   │   └── ApiErrorLogCaptureAppenderRegistrar.java
        │   ├── mask/
        │   │   ├── AuditMaskEngine.java
        │   │   ├── AuditObjectInspector.java
        │   │   ├── MaskDetector.java
        │   │   └── MaskUtil.java
        │   ├── model/
        │   │   ├── AuditData.java
        │   │   ├── ApiLogCapturedEvent.java
        │   │   ├── CapturedApiLogEvent.java
        │   │   ├── RequestData.java
        │   │   └── ResponseData.java
        │   ├── repository/
        │   │   └── TemporaryH2CaptureStore.java
        │   ├── resolver/
        │   │   ├── ApiExceptionLoggingResolver.java
        │   │   ├── AuditAuthenticationResolver.java
        │   │   └── LogEventSourceResolver.java
        │   ├── service/
        │   │   ├── ApiExceptionCaptureManager.java
        │   │   ├── AuditPublisher.java
        │   │   └── impl/DefaultAuditPublisher.java
        │   └── util/
        │       ├── Constants.java
        │       └── StringUtil.java
        └── resources/META-INF/spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## 13. Testes e cobertura

Execute a suíte automatizada com Maven:

```bash
mvn test
```

Os testes usam JUnit 5 e Mockito. O JaCoCo gera o relatório de cobertura em:

```text
target/site/jacoco/index.html
```

A suíte cobre mascaramento e limites da inspeção de objetos, isolamento do aspecto em relação ao fluxo de negócio, carregamento de campos sensíveis, resolução de autenticação, configuração, modelos, publicação de eventos de log, fila limitada, gravação/consulta H2 e expiração da sessão.

A captura temporária não usa entidades ORM: `TemporaryH2CaptureStore` gerencia o schema e as operações JDBC da base H2 em memória. Por isso, não há pacote `entity` nesta implementação. Os objetos HTTP de entrada e saída ficam no pacote `dto`, a coordenação da sessão no pacote `service`, e a integração com Logback no pacote `logging`.

## 14. Versionamento

A versão documentada neste README é `0.2.0`, conforme o `pom.xml` fornecido.

### Alterações da versão 0.2.0

- Migração do `groupId` para `com.siquaras` e dos pacotes Java para `com.siquaras.auditlog`.
- Renomeação dos pacotes internos por responsabilidade (`controller`, `dto`, `logging`, `repository`, `resolver` e `service`).
- Atualização dos exemplos de instalação e dependência para as novas coordenadas Maven.
- Captura temporária de logs e exceções em H2, controlada em tempo de execução por endpoint.
- Inclusão de `sourceClass` e `sourceMethod` para identificar a origem de logs e exceções capturados.
- Limites para fila, tamanho dos eventos e armazenamento em memória.
- Falhas de auditoria isoladas do resultado e das exceções das operações de negócio.
- Sanitização de coleções e proteção contra referências cíclicas na inspeção de objetos.
- Dependências Spring Boot e Jackson alinhadas pelo BOM do Spring Boot.

Ao publicar uma nova versão:

1. Atualize a versão no `pom.xml`.
2. Revise compatibilidade de Java, Spring Boot e Jackson.
3. Execute os testes e a validação de build.
4. Publique no repositório Maven corporativo.
5. Atualize este README com as alterações de API, configuração e compatibilidade.

## 15. Limitações conhecidas da versão documentada

- O publisher padrão escreve no log SLF4J; não há integração nativa com banco, Kafka ou outro destino externo.
- Não há propriedades próprias para ligar/desligar globalmente a auditoria ou configurar o publisher.
- O campo `headers` de `RequestData` não é preenchido pelo aspecto.
- A identificação do usuário depende do contexto Spring Security e, para claims específicos, dos nomes `userid` e `user_name`.
- A coleta de resposta usa reflexão sobre campos declarados diretamente na classe.
- A coleta dos parâmetros depende da disponibilidade dos nomes dos parâmetros na compilação.
- Chamadas internas entre métodos da mesma instância não são interceptadas pelo proxy padrão do Spring AOP.

---

**Documento atualizado para a versão `0.2.0`, conforme o `pom.xml` atual.** Endereço do repositório GIS, instruções corporativas de credenciais e política oficial de publicação Maven devem ser preenchidos pela equipe responsável pelo ambiente.
