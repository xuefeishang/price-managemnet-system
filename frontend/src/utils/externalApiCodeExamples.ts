import type { ExternalApiEndpoint } from '@/types/apiKey'

export type CodeExampleLanguage = 'node' | 'java' | 'postman' | 'powershell' | 'curl'

export interface EndpointSchemaField {
  name: string
  type: string
  required?: boolean
  defaultValue?: string | number | boolean
  description?: string
}

export interface CodeExampleInput {
  endpoint: ExternalApiEndpoint
  baseUrl: string
  appId: string
  appSecret: string
  usePlaceholders?: boolean
}

interface PreparedEndpointRequest {
  method: string
  path: string
  query: Record<string, unknown>
  queryString: string
  body: unknown
  bodyText: string
}

export const SIGNATURE_TEST_VECTOR = {
  secret: 'sec_test_1234567890',
  method: 'GET',
  path: '/api/external/v1/products',
  query: { size: 20, page: 0 },
  canonicalQuery: 'page=0&size=20',
  timestamp: '1779990000',
  nonce: 'nonce_test_001',
  bodySha256Hex: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
  signature: '7221cc7d6fc7d2f6cde1c20d7cdf62aa9669e0c965fe9e91902efac24d4e37cf'
} as const

export function parseJsonObject(value?: string): Record<string, unknown> {
  if (!value || !value.trim()) {
    return {}
  }
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

export function parseJsonArray<T>(value?: string): T[] {
  if (!value || !value.trim()) {
    return []
  }
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function buildCanonicalQuery(params: Record<string, unknown>): string {
  return Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .flatMap(([key, value]) => Array.isArray(value) ? value.map(item => [key, item]) : [[key, value]])
    .sort(([aKey, aValue], [bKey, bValue]) => {
      const keyCompare = String(aKey).localeCompare(String(bKey))
      return keyCompare || String(aValue).localeCompare(String(bValue))
    })
    .map(([key, value]) => `${encodeURIComponent(String(key))}=${encodeURIComponent(String(value))}`)
    .join('&')
}

export function buildCanonicalString(
  method: string,
  path: string,
  queryString: string,
  timestamp: string,
  nonce: string,
  bodySha256Hex: string
) {
  return [method.toUpperCase(), path, queryString, timestamp, nonce, bodySha256Hex].join('\n')
}

export function validateSignatureTestVector() {
  const query = buildCanonicalQuery(SIGNATURE_TEST_VECTOR.query)
  const canonical = buildCanonicalString(
    SIGNATURE_TEST_VECTOR.method,
    SIGNATURE_TEST_VECTOR.path,
    query,
    SIGNATURE_TEST_VECTOR.timestamp,
    SIGNATURE_TEST_VECTOR.nonce,
    SIGNATURE_TEST_VECTOR.bodySha256Hex
  )
  return {
    canonicalQueryMatches: query === SIGNATURE_TEST_VECTOR.canonicalQuery,
    canonicalString: canonical
  }
}

export function buildCodeExample(language: CodeExampleLanguage, input: CodeExampleInput): string {
  if (language === 'java') {
    return buildJavaExample(input)
  }
  if (language === 'postman') {
    return buildPostmanExample(input)
  }
  if (language === 'powershell') {
    return buildPowerShellExample(input)
  }
  if (language === 'curl') {
    return buildCurlExample(input)
  }
  return buildNodeExample(input)
}

export function buildNodeExample(input: CodeExampleInput): string {
  const prepared = prepareEndpointRequest(input.endpoint)
  const bodyValue = prepared.bodyText ? JSON.stringify(prepared.body, null, 2) : 'null'
  return `import crypto from 'node:crypto'

const APP_ID = ${quoteJs(input.appId)}
const APP_SECRET = ${quoteJs(input.appSecret)}
const BASE_URL = ${quoteJs(trimTrailingSlash(input.baseUrl))}

const METHOD = ${quoteJs(prepared.method)}
const PATH = ${quoteJs(prepared.path)}
const QUERY_PARAMS = ${JSON.stringify(prepared.query, null, 2)}
const BODY = ${bodyValue}

function sha256Hex(text) {
  return crypto.createHash('sha256').update(text, 'utf8').digest('hex')
}

function hmacSha256Hex(secret, text) {
  return crypto.createHmac('sha256', secret).update(text, 'utf8').digest('hex')
}

function canonicalQuery(params) {
  return Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .flatMap(([key, value]) => Array.isArray(value) ? value.map(item => [key, item]) : [[key, value]])
    .sort(([aKey, aValue], [bKey, bValue]) => aKey.localeCompare(bKey) || String(aValue).localeCompare(String(bValue)))
    .map(([key, value]) => \`\${encodeURIComponent(key)}=\${encodeURIComponent(String(value))}\`)
    .join('&')
}

async function callApi() {
  const query = canonicalQuery(QUERY_PARAMS)
  const bodyText = BODY === null ? '' : JSON.stringify(BODY)
  const timestamp = Math.floor(Date.now() / 1000).toString()
  const nonce = crypto.randomBytes(16).toString('hex')
  const bodyHash = sha256Hex(bodyText)
  const canonicalString = [METHOD, PATH, query, timestamp, nonce, bodyHash].join('\\n')
  const signature = hmacSha256Hex(APP_SECRET, canonicalString)
  const url = \`\${BASE_URL}\${PATH}\${query ? \`?\${query}\` : ''}\`

  const response = await fetch(url, {
    method: METHOD,
    headers: {
      'X-App-Id': APP_ID,
      'X-Timestamp': timestamp,
      'X-Nonce': nonce,
      'X-Signature': signature,
      ...(bodyText ? { 'Content-Type': 'application/json' } : {})
    },
    ...(bodyText ? { body: bodyText } : {})
  })

  const text = await response.text()
  if (!response.ok) {
    throw new Error(\`HTTP \${response.status}: \${text}\`)
  }
  console.log(text)
}

callApi().catch(error => {
  console.error(error)
  process.exit(1)
})
`
}

export function buildJavaExample(input: CodeExampleInput): string {
  const prepared = prepareEndpointRequest(input.endpoint)
  const queryMap = toJavaQueryMap(prepared.query)
  const body = prepared.bodyText ? javaTextBlock(prepared.bodyText) : '""'
  const exportBlock = input.endpoint.pathPattern.includes('export')
    ? `    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      Files.write(Paths.get("external-api-export.xlsx"), response.body());
      System.out.println("Saved to external-api-export.xlsx");
    } else {
      System.err.println("HTTP " + response.statusCode() + ": " + new String(response.body(), StandardCharsets.UTF_8));
      System.exit(1);
    }`
    : `    String responseText = new String(response.body(), StandardCharsets.UTF_8);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      System.out.println(responseText);
    } else {
      System.err.println("HTTP " + response.statusCode() + ": " + responseText);
      System.exit(1);
    }`

  return `import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExternalApiClientExample {
  private static final String APP_ID = ${quoteJava(input.appId)};
  private static final String APP_SECRET = ${quoteJava(input.appSecret)};
  private static final String BASE_URL = ${quoteJava(trimTrailingSlash(input.baseUrl))};
  private static final String METHOD = ${quoteJava(prepared.method)};
  private static final String PATH = ${quoteJava(prepared.path)};
  private static final String BODY = ${body};

  public static void main(String[] args) throws Exception {
${queryMap}
    String query = canonicalQuery(queryParams);
    String timestamp = String.valueOf(Instant.now().getEpochSecond());
    String nonce = nonceHex();
    String bodyHash = sha256Hex(BODY);
    String canonicalString = String.join("\\n", METHOD, PATH, query, timestamp, nonce, bodyHash);
    String signature = hmacSha256Hex(APP_SECRET, canonicalString);
    String url = BASE_URL + PATH + (query.isEmpty() ? "" : "?" + query);

    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("X-App-Id", APP_ID)
        .header("X-Timestamp", timestamp)
        .header("X-Nonce", nonce)
        .header("X-Signature", signature);

    if (BODY.isEmpty()) {
      builder.method(METHOD, HttpRequest.BodyPublishers.noBody());
    } else {
      builder.header("Content-Type", "application/json");
      builder.method(METHOD, HttpRequest.BodyPublishers.ofString(BODY, StandardCharsets.UTF_8));
    }

    HttpResponse<byte[]> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
${exportBlock}
  }

  private static String canonicalQuery(Map<String, List<String>> params) {
    List<String> parts = new ArrayList<>();
    params.forEach((key, values) -> {
      if (values == null || values.isEmpty()) {
        parts.add(encode(key) + "=");
      } else {
        List<String> sortedValues = new ArrayList<>(values);
        Collections.sort(sortedValues);
        sortedValues.forEach(value -> parts.add(encode(key) + "=" + encode(value)));
      }
    });
    Collections.sort(parts);
    return String.join("&", parts);
  }

  private static String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
        .replace("+", "%20")
        .replace("%7E", "~");
  }

  private static String sha256Hex(String text) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
  }

  private static String hmacSha256Hex(String secret, String text) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
  }

  private static String nonceHex() {
    byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
`
}

export function buildPostmanExample(input: CodeExampleInput): string {
  const prepared = prepareEndpointRequest(input.endpoint)
  const queryHint = prepared.queryString ? `?${prepared.queryString}` : ''
  const bodyHint = prepared.bodyText || ''
  return `Postman 配置
1. Environment 变量：
   base_url = ${trimTrailingSlash(input.baseUrl)}
   app_id = ${input.appId}
   app_secret = ${input.appSecret}

2. Request：
   Method = ${prepared.method}
   URL = {{base_url}}${prepared.path}${queryHint}
${bodyHint ? `   Body = raw / JSON\n${indentLines(bodyHint, '   ')}` : '   Body = none'}

3. Pre-request Script：复制下面脚本到 Postman 的 Pre-request Script。

const appId = pm.environment.get('app_id')
const appSecret = pm.environment.get('app_secret')

if (!appId || !appSecret) {
  throw new Error('请先在 Postman Environment 中配置 app_id 和 app_secret')
}

function encodeComponent(value) {
  return encodeURIComponent(String(value))
}

function canonicalQueryFromUrl(url) {
  const query = url.query ? url.query.all() : []
  const parts = []
  query.forEach(item => {
    if (item.disabled) {
      return
    }
    const key = item.key || ''
    const value = item.value == null ? '' : item.value
    if (key === '') {
      return
    }
    parts.push(encodeComponent(pm.variables.replaceIn(key)) + '=' + encodeComponent(pm.variables.replaceIn(value)))
  })
  return parts.sort().join('&')
}

function getBodyText() {
  if (!pm.request.body || pm.request.body.isEmpty()) {
    return ''
  }
  if (pm.request.body.mode === 'raw') {
    return pm.request.body.raw || ''
  }
  return ''
}

const method = pm.request.method.toUpperCase()
const path = pm.variables.replaceIn('/' + pm.request.url.path.join('/'))
const query = canonicalQueryFromUrl(pm.request.url)
const bodyText = pm.variables.replaceIn(getBodyText())
const timestamp = Math.floor(Date.now() / 1000).toString()
const nonce = CryptoJS.lib.WordArray.random(16).toString(CryptoJS.enc.Hex)
const bodyHash = CryptoJS.SHA256(CryptoJS.enc.Utf8.parse(bodyText)).toString(CryptoJS.enc.Hex)
const canonicalString = [method, path, query, timestamp, nonce, bodyHash].join('\\n')
const signature = CryptoJS.HmacSHA256(canonicalString, appSecret).toString(CryptoJS.enc.Hex)

pm.request.headers.upsert({ key: 'X-App-Id', value: appId })
pm.request.headers.upsert({ key: 'X-Timestamp', value: timestamp })
pm.request.headers.upsert({ key: 'X-Nonce', value: nonce })
pm.request.headers.upsert({ key: 'X-Signature', value: signature })

if (bodyText) {
  pm.request.headers.upsert({ key: 'Content-Type', value: 'application/json' })
}
`
}

export function buildPowerShellExample(input: CodeExampleInput): string {
  const prepared = prepareEndpointRequest(input.endpoint)
  const body = prepared.bodyText ? JSON.stringify(prepared.body, null, 2) : ''
  const escapedBody = body ? `@'\n${body}\n'@` : '""'
  const requestBlock = input.endpoint.pathPattern.includes('export')
    ? 'Invoke-WebRequest -Method $Method -Uri $Uri -Headers $Headers -OutFile ".\\external-api-export.xlsx"\nWrite-Host "已保存到 .\\external-api-export.xlsx"'
    : `if ($Body) {
  $Response = Invoke-RestMethod -Method $Method -Uri $Uri -Headers $Headers -Body $Body -ContentType "application/json"
} else {
  $Response = Invoke-RestMethod -Method $Method -Uri $Uri -Headers $Headers
}

$Response | ConvertTo-Json -Depth 20`

  return `$AppId = ${quotePowerShell(input.appId)}
$AppSecret = ${quotePowerShell(input.appSecret)}
$BaseUrl = ${quotePowerShell(trimTrailingSlash(input.baseUrl))}
$Method = ${quotePowerShell(prepared.method)}
$Path = ${quotePowerShell(prepared.path)}
$Query = ${quotePowerShell(prepared.queryString)}
$Body = ${escapedBody}
$Timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
$Nonce = [Guid]::NewGuid().ToString("N")

function Get-Sha256Hex([string]$Text) {
  $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
  $hash = [Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
  return -join ($hash | ForEach-Object { $_.ToString("x2") })
}

function Get-HmacSha256Hex([string]$Secret, [string]$Text) {
  $key = [Text.Encoding]::UTF8.GetBytes($Secret)
  $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
  $hmac = [Security.Cryptography.HMACSHA256]::new($key)
  $hash = $hmac.ComputeHash($bytes)
  return -join ($hash | ForEach-Object { $_.ToString("x2") })
}

$BodyHash = Get-Sha256Hex $Body
$Canonical = @($Method, $Path, $Query, $Timestamp, $Nonce, $BodyHash) -join "\`n"
$Signature = Get-HmacSha256Hex $AppSecret $Canonical
$Uri = if ($Query) { "$BaseUrl$Path" + "?" + $Query } else { "$BaseUrl$Path" }
$Headers = @{
  "X-App-Id" = $AppId
  "X-Timestamp" = $Timestamp
  "X-Nonce" = $Nonce
  "X-Signature" = $Signature
}

${requestBlock}
`
}

export function buildCurlExample(input: CodeExampleInput): string {
  const prepared = prepareEndpointRequest(input.endpoint)
  const bodyAssignment = prepared.bodyText ? `BODY='${escapeSingleQuotedShell(prepared.bodyText)}'` : 'BODY=""'
  const bodyCurl = prepared.bodyText
    ? ` \\
  -H "Content-Type: application/json" \\
  --data "$BODY"`
    : ''
  const outputCurl = input.endpoint.pathPattern.includes('export') ? ' \\\n  -o external-api-export.xlsx' : ''

  return `APP_ID="${escapeDoubleQuotedShell(input.appId)}"
APP_SECRET="${escapeDoubleQuotedShell(input.appSecret)}"
BASE_URL="${escapeDoubleQuotedShell(trimTrailingSlash(input.baseUrl))}"
METHOD="${prepared.method}"
PATH="${prepared.path}"
QUERY="${escapeDoubleQuotedShell(prepared.queryString)}"
${bodyAssignment}
TIMESTAMP="$(date +%s)"
NONCE="$(openssl rand -hex 16)"
BODY_SHA256="$(printf "%s" "$BODY" | openssl dgst -sha256 -hex | awk '{print $2}')"
CANONICAL="\${METHOD}
\${PATH}
\${QUERY}
\${TIMESTAMP}
\${NONCE}
\${BODY_SHA256}"
SIGNATURE="$(printf "%s" "$CANONICAL" | openssl dgst -sha256 -hmac "$APP_SECRET" -hex | awk '{print $2}')"
URL="$BASE_URL$PATH"
if [ -n "$QUERY" ]; then
  URL="$URL?$QUERY"
fi

curl -X "$METHOD" "$URL" \\
  -H "X-App-Id: $APP_ID" \\
  -H "X-Timestamp: $TIMESTAMP" \\
  -H "X-Nonce: $NONCE" \\
  -H "X-Signature: $SIGNATURE"${bodyCurl}${outputCurl}
`
}

export function prepareEndpointRequest(endpoint: ExternalApiEndpoint): PreparedEndpointRequest {
  const method = endpoint.method.toUpperCase()
  const query = parseJsonObject(endpoint.queryExample)
  const queryString = buildCanonicalQuery(query)
  const pathParams = parseJsonObject(endpoint.pathParamsExample)
  const path = applyPathParams(endpoint.pathPattern, pathParams)
  const body = method === 'GET' || method === 'DELETE' ? null : parseJsonObject(endpoint.bodyExample)
  const bodyText = body && Object.keys(body as Record<string, unknown>).length > 0 ? JSON.stringify(body) : ''

  return {
    method,
    path,
    query,
    queryString,
    body,
    bodyText
  }
}

export function endpointSchemaFields(value?: string): EndpointSchemaField[] {
  return parseJsonArray<EndpointSchemaField>(value)
}

function applyPathParams(pathPattern: string, pathParams: Record<string, unknown>) {
  let path = pathPattern
  Object.entries(pathParams).forEach(([key, value]) => {
    path = path.replace(`{${key}}`, encodeURIComponent(String(value)))
  })
  if (path.includes('**')) {
    path = path.replace('**', encodeURIComponent(String(pathParams.wildcard || 'dashboard')))
  }
  const wildcardValue = pathParams.productId || pathParams.id || 1
  while (path.includes('*')) {
    path = path.replace('*', encodeURIComponent(String(wildcardValue)))
  }
  return path
}

function trimTrailingSlash(value: string) {
  return (value || 'http://localhost:8080').replace(/\/+$/, '')
}

function quoteJs(value: string) {
  return JSON.stringify(value)
}

function quotePowerShell(value: string) {
  return `"${value.replace(/`/g, '``').replace(/"/g, '`"')}"`
}

function quoteJava(value: string) {
  return `"${value
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\r/g, '\\r')
    .replace(/\n/g, '\\n')
    .replace(/\t/g, '\\t')}"`
}

function javaTextBlock(value: string) {
  return `"""\n${value.replace(/"""/g, '\\"""')}\n"""`.trim()
}

function toJavaQueryMap(query: Record<string, unknown>) {
  const entries = Object.entries(query).filter(([, value]) => value !== undefined && value !== null && value !== '')
  const lines = ['    Map<String, List<String>> queryParams = new LinkedHashMap<>();']
  entries.forEach(([key, value]) => {
    if (Array.isArray(value)) {
      const values = value.map(item => quoteJava(String(item))).join(', ')
      lines.push(`    queryParams.put(${quoteJava(key)}, List.of(${values}));`)
    } else {
      lines.push(`    queryParams.put(${quoteJava(key)}, List.of(${quoteJava(String(value))}));`)
    }
  })
  return lines.join('\n')
}

function indentLines(value: string, prefix: string) {
  return value.split('\n').map(line => `${prefix}${line}`).join('\n')
}

function escapeSingleQuotedShell(value: string) {
  return value.replace(/'/g, `'\\''`)
}

function escapeDoubleQuotedShell(value: string) {
  return value.replace(/["\\$`]/g, '\\$&')
}
