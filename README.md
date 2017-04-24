# RxJava 구현해 보기

뭔가를 배우는 과정에서 이해를 넓히는 가장 확실한 방법은 직접 해보는 것이다. Reactive Programming 에 많은 관심들이 있는데, RxJava 를 직접 구현해 보면서 내부 동작을 이해해보자.
(많은 사람들이 익숙해하고 있는 Java를 선택했다.)
필요한 기술을 짧게만 소개하고 바로 구현에 들어간다.

## 1. Delegation Pattern

![Delegation Patterm](http://best-practice-software-engineering.ifs.tuwien.ac.at/patterns/images/delegation_advanced.jpg)(http://best-practice-software-engineering.ifs.tuwien.ac.at/patterns/delegation.html)

특정 작업에 대해 인터페이스만 정해놓고 상세 구현은 다른 오브젝트 등에 두어 동작을 위임하는 형태이다. 버튼 클릭 이벤트 같은 경우 그 예를 볼 수 있다. (안드로이드의 예)
이 예는 그냥 콜백 아니냐 라고 볼 수 있겠지만, 실행 코드블록을 장입해 놓고 필요시에 호출해서 사용한다는 측면으로 보면 Delegation Pattern 이다.

```java
Button btn = (Button)findViewById(R.id.button);
btn.setOnClickListener( new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        System.out.println(“Button Clicked”);
    }
});
``` 

버튼이 클릭되면 수행해야 할 내용을 OnClickListener 인터페이스를 구현한 인스턴스에게 위임해서 콜백으로 처리하는 경우이다.

## 2. Method Chaining

메소드의 수행 결과를 자신의 인스턴스를 넘김으로써 메소드를 연결해서 사용할 수 있는 방법이다.

```java
class Chaining {
    public Chaining foo() {
    }
    public Chaining bar() {
    }
}

new Chaining().foo().bar();
```

## 3. 이제 구현해 보자

필요한 기술이 이게 다야? 스케줄링과 쓰레드는 얘기할게 많으니까 이건 생략한다. 그거 빼고 나면 이것 만으로도 충분하다. 사실 아이디어가 훌륭한거지 구현은 그리 복잡하지 않다.

### 3-1. 따라서 만들 내용

```java
Integer[] ints = new Integer[] { 1,2,3,4,5,6,7,8,9,10 };
Observable.from(ints)
	.filter(i -> i % 2 == 0)
	.map(i -> i * 10)
	.subscribe(i -> System.out.println(i + ","));
//20, 40, 60, 80, 100
```

이렇게 동작하는 Observable을 만들어 볼 거다. 처음엔 reduce 넣고 sum을 구해서 map으로 문자열로 만들다음 strSum 을 출력하는 예제였는데, 쓰다보기 길어져서 그냥 내용을 잘랐다.

### 3-2. Observable 클래스 만들기

클래스 설계부터 보자. Observable 에서 from 은 클래스 메소드이고 이후 filter나 map, subscribe 는 인스턴스 메소드 이면 될 것 같다.

```java
class Observable {
    public static Observable from(Integer[] array);
    public Observable filter( … );
    public Observable map( … );
    public void subscribe( … );
}
```

filter 와 map 은 구현부를 인자로 받는다. 이 부분을 delegation 패턴을 사용하도록 하자.  이를 위한 인터페이스를 이렇게 정의하겠다. 일단은 Generic 은 생각하지 않고 만들어 보자.

```java
interface FilterDelegation {
    public boolean condition(Integer i);
}

interface MapDelegation {
    public Integer transform(Integer i);
}
```

그러면 Observable 클래스의 filter와 map 에 인자는 이 인터페이스로 받아들이면 되겠다.

subscribe 메소드는 더 이상 메소드 체이닝을 진행시키지 않는다. 리턴값은 void가 되고, 인자로는 결과로 받은 값을 처리할 delegation이 필요하다.

```java
interface SubscribeDelegation {
    public void subs(Integer i);
}
```

최종으로 얻은 Observable 클래스는 다음과 같이 되겠다.

```java
class Observable {
    public static Observable from(Integer[] array);
    public Observable filter( FilterDelegation f );
    public Observable map( MapDelegation m );
    public void subscribe( SubscribeDelegation s );
}
```

이제 메소드 내용을 구현만 하면 되겠다. 하나씩 채워나가 보자.

### 3-3. 설계

구현 전에 동작을 구상해 보자.
from 에서 들어온 배열들을 하나씩 꺼내서 subscribe 에 전달하면 되겠다. 그럼 루프를 사용해서 처리하면 될까? 
그 과정에서 filter 와 map 을 거치게 되는데, 사용자에 의해서 순서나 횟수가 얼마나 들어오게 될 지 모른다. 어떻게 해야 할까? 
각각의 값이 모두 정해진 filter나 map을 거쳐야 하니까 그럼 값 하나씩 마다 처리하게 하자. 그래서 끝까지 살아남은 것들은 subscribe에 전달하자.
그렇다면 from 으로 들어온 값들은 Iteration 으로 멤버로 들고 있고 필요할 때마다 하나씩 꺼내쓰자.
map 이나 filter가 호출될 때마다 처리할 오퍼레이션이 늘어가게 되니까 Operators라는 배열에 각 작업을 넣어두었다가, iteration 에서 값을 하나 꺼내서 이 Operators 배열의 순서대로 하나씩 적용시키다가 최종에 나온 것만 subscribe 에 전달해 주자.
그렇다면 filter 나 map 작업을 오브젝트로 만들 필요가 있겠고, 이 오브젝트를 담을 컬렉션이 필요하다. 
음… filter, map 같은 작업을 나타낼 대표할 클래스나 인터페이스가 필요하겠군.
그렇게 된다면 filter 에서는 filter 작업을 표현하는 Operator 인스턴스를 만들어서 작업배열에 담아두고 메소드 체인을 위해서 this를 리턴하는 형식의 구현이 되겠군.
그렇다면 subscribe 는 iterator 에서 값이 없을 때까지 하나씩 꺼내다가 operator들을 적용시킨 다음에 의미있는 값들을 delegation 에 전달하면 되겠군..

### 3-4. Operator 클래스

Operation 을 수행한 후에 이 값을 전달해도 될 것인지 아닌지를 확인하기 위해서 리턴값은 boolean으로 하는 인터페이스를 하나 만들어 보자.

```java
interface Operator {
    public boolean apply(Integer i);
}
```

이렇게 인터페이스를 하나 만들어 봤다. 그리고 이 것을 담은 가변 배열을 준비해 두자

```java
ArrayList<Operator> operators = new ArrayList<Operator>();
```

그러고 보니, Operation 을 수행하고 난 결과를 받아주는 곳이 없다. 리턴값에 있어야 할 것 같은데.. 그렇다는 것은 Operation 결과는 의미가 있는지 없는지, 의미가 있다면 값은 무엇인지 까지 나타내는 리턴값이 전달되어야 하겠다. 리턴값을 나타내는 클래스를 하나 만들자 그리고 Operator 인터페이스는 그 클래스를 리턴하는 것으로 바꾸자

```java
class OperationResult {
    boolean isValid;
    Integer value;
}
	
interface Operator {
    public OperationResult apply(Integer i);
}
```

이제, filter 를 구현해 보자.

```java
public Observable filter(FilterDelegation f) {

	class FilterOperation implements Operator {
		FilterDelegation f;

		@Override
		public OperationResult apply(Integer i) {
			boolean isValid = f.condition(i);
			OperationResult result = new OperationResult();
			result.isValid = isValid;
			result.value = i;
			return result;
		}
	}

	FilterOperation op = new FilterOperation();
	op.f = f;

	operators.add(op);

	return this;
}
```

일단, Operator 를 구현한 nested 클래스를 만들어서 delegate 를 내장시키고, opertors 에 추가한다.
Operator 의 구현은 delegation 을 통해서 얻은 결과값을 OperatinoResult 에 담아서 리턴하는데, valid 여부는 delegation의 리턴값을 사용한다.

map 도 동일한 형태로 구현될 수 있겠다.

```java
public Observable map(MapDelegation m) {
	
	class MapOperation implements Operator {

		MapDelegation m;
		
		@Override
		public OperationResult apply(Integer i) {
			Integer r= m.transform(i);
			OperationResult result = new OperationResult();
			result.isValid = r != null;
			result.value = r;
			return result;
		}
	}
	
	MapOperation op = new MapOperation();
	op.m = m;
	
	operators.add(op);
	
	return this;
}
```

### 3-5. from 과 subscribe 구현하기

from은 static 메소드로 Observable을 생성하고, 내부에 있는 iterator 에 인자로 주어진 배열을 전달한다.

```java
Iterator<Integer> dataSource;

public static Observable from(Integer[] array) {
	Observable ob = new Observable();
	ob.dataSource = Arrays.asList(array).iterator();
	return ob;
}
```

구현은 간단하다

subscribe 가 이제 해야할 일이 많다.
여태까지 등록된 dataSource 에서 값을 하나씩 꺼내어 operator 의 처리들을 거치도록 하고 valid 한 최종 값을 delegation에 전달하도록 한다.

```java
public void subscribe(SubscribeDelegation s) {
	while(dataSource.hasNext()) {
		Integer data = dataSource.next();
		
		OperationResult lastResult = new OperationResult();
		lastResult.isValid = true;
		lastResult.value = data;
		
		for(Operator op : operators) {
			lastResult = op.apply(lastResult.value);
			if(lastResult.isValid == false) break;
		}
		
		if(lastResult.isValid) {
			s.subs(lastResult.value);
		}
	}
}
```

코드는 복잡할 것 없이 생각한 그대로 구현되었다.

## 4. 실행해보기

이제 모든 구현이 끝났다.
처음 RxJava로 수행했던 코드를 그대로 적용해 보자

```java
Integer[] ints = new Integer[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };

Observable.from(ints)
		.filter(i -> i % 2 == 0)
		.map(i -> i * 10)
		.subscribe(i -> System.out.print(i + “,"));
//20,40,60,80,100
```

Java 8 에서 수행한다면, 람다식을 사용하여 코드 수정 없이 그대로 정상 동작하는 것을 볼 수 있다.

이번에는 filter와 map을 좀 더 적용해 보자.

```java
Integer[] ints = new Integer[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
Observable.from(ints)
	.filter(i -> i % 2 == 0)
	.map(i -> i * 10)
	.filter(i -> i < 100)
	.map(i -> i + 1)
	.map(i -> i * 2)
	.map(i -> i + 3)
	.filter(i -> i % 25 == 0)
	.subscribe(i -> System.out.print(i + “,”));
//125,
```

filter와 map 의 내용은 의미 없다. 그냥 많이 섞어서 적용해 본 것 뿐이다.
결과를 예상대로 잘 동작한다.

## 5. 한 걸음 더 나아가기

- **Operators (난이도 하)**<br/>
이 예제는 filter와 map 만을 구현해 보았으나 그 외 수많은 Operator 들을 구현해 보면 RxJava에 대해 더 깊이 이해할 수 있게 될 것이다.

- **Generic (난이도 중)**<br/>
여기서는 Integer[] 를 처리하는 경우의 예만 들어보았다. String도 처리할 수 있어야 겠고 다른 타입도 할 수 있어야 할 것이다.
map 같이 중간에 타입이 변경되는 경우에 대한 세심한 처리가 필요하다

- **Schedule (난이도 상)**<br/>
observeOn 이나 subscribeOn 등의 구현으로 Thread 를 이동해 가면서 OperationData 를 전달해 본다.

- **flatMap(난이도 상)**<br/>
flatMap 은 그냥 map과 다르다. flatMap 의 수행 결과로 Observable이 전달 된다면 전달된 Observable 의 operation 들을 flatMap이 수행된 Observable의 operation과 함께 적용할 수 있어야 한다.
dataSource 를 Observable 로 감쌌는데, 감싼 놈에게 감싼 데이터가 들어오면 두 번 감싼 데이터로 만드는게 아니라 나중에 들어온 것을 한꺼풀 벗겨서 최종 하나만 감싼 놈으로 만드는 처리를 해야 한다.
설명이 성의없어 보이지만 이게 가장 쉽게 설명한 거다.


한 걸음 더 나아가는 것은 ~~내가 귀찮으니~~ 각자의 몫으로 남겨두자.