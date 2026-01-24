# Performance Benchmark figures

## Round 1 - without JVM args

> JMH version: 1.37<br />
> VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS<br />
> VM options: -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8<br />
> Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)<br />
> Warmup: 1 iterations, 10 s each<br />
> Measurement: 3 iterations, 30 s each<br />
> Timeout: 10 min per iteration<br />
> Threads: 1 thread, will synchronize iterations<br />
> Benchmark mode: Average time, time/op

### Benchmark Results 

| Benchmark                  | Record Count | Mode | Cnt | Score (ms/op)  | Error (ms/op) | Units |
|:---------------------------|:-------------| :--- |:----|:---------------|:--------------|:------|
| `benchmarkParseAndConvert` |   100,000    | avgt | 3   |   **9,254.63** | ±     679.14  | ms/op |
| `benchmarkParseAndConvert` |   500,000    | avgt | 3   |  **49,544.51** | ±  40,763.68  | ms/op |
| `benchmarkParseAndConvert` | 1,000,000    | avgt | 3   |  **99,761.47** | ±  18,354.46  | ms/op |
| `benchmarkParseAndConvert` | 2,000,000    | avgt | 3   | **249,852.32** | ± 217,321.07  | ms/op |
| `benchmarkStringify`       |   100,000    | avgt | 3   |   **2,006.63** | ±     427.77  | ms/op |
| `benchmarkStringify`       |   500,000    | avgt | 3   |  **10,000.91** | ±   1,747.14  | ms/op |
| `benchmarkStringify`       | 1,000,000    | avgt | 3   |  **25,224.34** | ± 137,220.82  | ms/op |
| `benchmarkStringify`       | 2,000,000    | avgt | 3   |  **70,576.00** | ±  85,604.88  | ms/op |

## Round 2 - using JVM args

> JMH version: 1.37<br />
> VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS<br />
> <span style="color: red;font-weight: bold;">VM options: -Xms4G -Xmx4G -XX:+UseZGC -XX:+ZGenerational <br /></span>
> Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)<br />
> Warmup: 1 iterations, 10 s each<br />
> Measurement: 3 iterations, 30 s each<br />
> Timeout: 10 min per iteration<br />
> Threads: 1 thread, will synchronize iterations<br />
> Benchmark mode: Average time, time/op

### Benchmark Results

| Benchmark                  | Record Count | Mode | Cnt | Score (ms/op)  | Error (ms/op) | Units |
|:---------------------------|:-------------| :--- |:----|:---------------|:--------------|:------|
| `benchmarkParseAndConvert` | 1,000,000    | avgt | 3   | **135,299.47** | ± 250,883.46  | ms/op |
| `benchmarkParseAndConvert` | 2,000,000    | avgt | 3   | **232,988.75** | ±  95,146.45  | ms/op |
| `benchmarkStringify`       | 1,000,000    | avgt | 3   |  **41,803.48** | ± 164,299.68  | ms/op |
| `benchmarkStringify`       | 2,000,000    | avgt | 3   |  **77,561.11** | ± 158,505.21  | ms/op |


