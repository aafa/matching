### Market matching 

The goal here is to implement the mechanism to match incoming order requests from users and make sure that data is kept in a nice and consistent state.
I will do this in a transactional manner but matching only pairs of exactly opposite orders. For simplicity.

#### Implementation details
- Transactional operations, using `scala-stm` (https://nbronson.github.io/scala-stm/)
- Stream-read from input data file using `fs2`
- All transactions wrapped in a `Try` monad to collect full output results whether successful or not 

#### Run
- `sbt run` runs solver 
- `sbt test` runs unit tests

Tests covers all basic scenarios including failed transactions, rollbacked state, mixed successful and failed transactions for a given input data. 

#### Result.txt
See [results/results.txt](results/results.txt)

```
C1       2925    120     172     690     229
C2       4628    380     146     934     491
C3       989     57      40      43      81
C4       4040    359     425     428     695
C5       1294    42      31      364     117
C6       3721    676     264     99      34
C7       1912    31      6       681     24
C8       3327    194     242     122     160
C9       3634    281     274     119     379
```

#### Solver logs

```
> run
[info] Compiling 1 Scala source to /Users/aafa/Projects/sandbox/matching/target/scala-2.12/classes...
[info] Running Solver
[info] clients before
[info] C1       1000    130     240     760     320
[info] C2       4350    370     120     950     560
[info] C3       2760    0       0       0       0
[info] C4       560     450     540     480     950
[info] C5       1500    0       0       400     100
[info] C6       1300    890     320     100     0
[info] C7       750     20      0       790     0
[info] C8       7000    90      190     0       0
[info] C9       7250    190     190     0       280
[info] checksum 26470   2140    1600    3480    2210
[info]
[info] transactions
[info] Success(Transaction(Vector(Order(C2,Sell,C,14,5), Order(C9,Buy,C,14,5))))
[info] Success(Transaction(Vector(Order(C8,Buy,A,9,3), Order(C6,Sell,A,9,3))))
[info] Success(Transaction(Vector(Order(C9,Buy,B,5,3), Order(C1,Sell,B,5,3))))
[info] Success(Transaction(Vector(Order(C9,Sell,A,9,1), Order(C8,Buy,A,9,1))))
<... skipped ...>
[info] Success(Transaction(Vector(Order(C6,Buy,A,12,5), Order(C6,Sell,A,12,5))))
[info] Success(Transaction(Vector(Order(C2,Sell,D,3,4), Order(C3,Buy,D,3,4))))
[info]
[info] orders left
[info] Order(C4,Sell,D,5,2)
[info] Order(C2,Sell,D,6,1)
[info] Order(C8,Buy,B,5,5)
[info] Order(C4,Sell,C,15,3)
[info] Order(C1,Sell,B,6,2)
[info] Order(C6,Sell,A,10,1)
[info] Order(C3,Buy,A,9,1)
[info] Order(C9,Buy,D,3,3)
[info] Order(C2,Buy,B,7,3)
[info] Order(C2,Sell,C,12,5)
[info] Order(C9,Buy,D,4,3)
[info] Order(C8,Buy,A,11,2)
[info] Order(C7,Sell,C,14,3)
[info]
[info] clients after
[info] C1       2925    120     172     690     229
[info] C2       4628    380     146     934     491
[info] C3       989     57      40      43      81
[info] C4       4040    359     425     428     695
[info] C5       1294    42      31      364     117
[info] C6       3721    676     264     99      34
[info] C7       1912    31      6       681     24
[info] C8       3327    194     242     122     160
[info] C9       3634    281     274     119     379
[info] checksum 26470   2140    1600    3480    2210
[success] Total time: 1 s, completed 02.09.2017 10:44:29
>

```

For full logs see [results/solver.log](results/solver.txt)

#### TODO
- Implement partial orders (from left-over orders we can clearly see that we haven't covered that yet)
- Adopt streams wider, perform matching over stream of incoming orders data 