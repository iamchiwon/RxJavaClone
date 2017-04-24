import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class RxJavaClone {

	public static void main(String[] args) {
		Integer[] ints = new Integer[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
		
		Observable.from(ints)
				.filter(i -> i % 2 == 0)
				.map(i -> i * 10)
				.subscribe(i -> System.out.print(i + ","));

		System.out.println("\n----");
		
		Observable.from(ints)
				.filter(i -> i % 2 == 0)
				.map(i -> i * 10)
				.filter(i -> i < 100)
				.map(i -> i + 1)
				.map(i -> i * 2)
				.map(i -> i + 3)
				.filter(i -> i % 25 == 0)
				.subscribe(i -> System.out.print(i + ","));
	}

	//////// RxJava Clone

	public static interface FilterDelegation {
		public boolean condition(Integer i);
	}

	public static interface MapDelegation {
		public Integer transform(Integer i);
	}

	public static interface SubscribeDelegation {
		public void subs(Integer i);
	}

	static class OperationResult {
		boolean isValid;
		Integer value;
	}

	static interface Operator {
		public OperationResult apply(Integer i);
	}

	public static class Observable {

		Iterator<Integer> dataSource;
		ArrayList<Operator> operators = new ArrayList<Operator>();

		public static Observable from(Integer[] array) {
			Observable ob = new Observable();
			ob.dataSource = Arrays.asList(array).iterator();
			return ob;
		}

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

		public Observable map(MapDelegation m) {

			class MapOperation implements Operator {

				MapDelegation m;

				@Override
				public OperationResult apply(Integer i) {
					Integer r = m.transform(i);
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
	}

}
