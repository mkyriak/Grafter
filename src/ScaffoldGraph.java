import java.util.*;

/*
 * Map contig name -> contig name -> list of alignments
 */
public class ScaffoldGraph {
	
	static class Scaffolding
	{
		HashMap<String, ArrayDeque<String>> scaffoldContigs;
		HashMap<String, ArrayDeque<ScaffoldGraph.Alignment>> scaffoldEdges;
		HashSet<String> usedContigs;
		int numMerged;
		Scaffolding()
		{
			scaffoldContigs = new HashMap<>();
			scaffoldEdges = new HashMap<>();
			usedContigs = new HashSet<>();
			numMerged = 0;
		}
	}
	
	public Scaffolding globalScaffolding()
	{
		Scaffolding res = new Scaffolding();
		HashMap<String, String> lastToFirst = new HashMap<>();
		PriorityQueue<Alignment> pq = ScaffoldGraphBuilder.getAllSortedEdges(this);
		while(!pq.isEmpty())
		{
			Alignment best = pq.poll();
			if(!ScaffoldGraphBuilder.stillValid(best, res, lastToFirst))
			{
				continue;
			}
			
			if(Settings.VERBOSE)
			{
				System.err.println("Taking edge:");
				System.err.println(" from=" + best.from + ", myContigPrefix=" + best.myContigPrefix +
						", to=" + best.to + ", theirContigPrefix=" + best.theirContigPrefix);
			
			}
			
			// Once weights get too small, stop
			if(best.weight < Settings.MIN_WEIGHT_SUPPORT)
			{
				break;
			}
			
			String s = best.from;
			
			if(lastToFirst.containsKey(s) && res.scaffoldEdges.get(lastToFirst.get(s)).peekLast().theirContigPrefix == best.myContigPrefix)
			{
				continue;
			}
			String t = best.to;
			
			if(Settings.VERBOSE)
			{
				System.err.println("Confirmed edge addition from " + s + " to " + t);
			}
			
			if(!res.usedContigs.contains(t))
			{
				// t is by itself in a contig
				
				if(res.usedContigs.contains(s))
				{
					if(Settings.VERBOSE)
					{
						System.err.println("  From contig in larger scaffold to lone contig");
					}
					// s is the last contig in some scaffold
					String firstContigInScaffold = lastToFirst.get(s);
					ArrayDeque<String> allContigsInScaffold = res.scaffoldContigs.get(firstContigInScaffold);
					ArrayDeque<ScaffoldGraph.Alignment> allEdgesInScaffold = res.scaffoldEdges.get(firstContigInScaffold);
					allContigsInScaffold.addLast(t);
					allEdgesInScaffold.add(best);
					
					res.usedContigs.add(t);
					lastToFirst.remove(s);
					lastToFirst.put(t, firstContigInScaffold);
				}
				else
				{
					if(Settings.VERBOSE)
					{
						System.err.println("  From one lone contig to another");
					}
					// s hasn't been connected to anything yet
					res.scaffoldEdges.put(s, new ArrayDeque<ScaffoldGraph.Alignment>());
					res.scaffoldContigs.put(s, new ArrayDeque<String>());
					res.scaffoldContigs.get(s).addLast(s);
					res.scaffoldContigs.get(s).addLast(t);
					res.scaffoldEdges.get(s).addLast(best);
					res.usedContigs.add(s);
					res.usedContigs.add(t);
					lastToFirst.put(t, s);
				}
			}
			
			else
			{
				// In calculating best, already made sure it's the first or last in its scaffold
				// Move entire scaffold with t to the end of the scaffold with s
				boolean tFirst = res.scaffoldContigs.containsKey(t);
				String lastContigInScaffold = tFirst ? res.scaffoldContigs.get(t).peekLast() : lastToFirst.get(t);
				String tScaffoldKey = tFirst ? t : lastContigInScaffold;
				if(res.usedContigs.contains(s))
				{
					if(Settings.VERBOSE)
					{
						System.err.println("  From one scaffold to another");
					}
					// s is the last contig in a scaffold, so append the scaffold with to after s
					String firstContigInScaffold = lastToFirst.get(s);
					lastToFirst.remove(s);
					lastToFirst.put(lastContigInScaffold, firstContigInScaffold);
					res.scaffoldEdges.get(firstContigInScaffold).addLast(best);
					ArrayDeque<ScaffoldGraph.Alignment> tScaffoldEdges = res.scaffoldEdges.get(tScaffoldKey);
					if(tFirst)
					{
						while(!tScaffoldEdges.isEmpty())
						{
							ScaffoldGraph.Alignment cur = tScaffoldEdges.pollFirst();
							res.scaffoldEdges.get(firstContigInScaffold).addLast(cur);
						}
					}
					else
					{
						String lastTo = tScaffoldKey;
						ArrayDeque<ScaffoldGraph.Alignment> toAdd = new ArrayDeque<ScaffoldGraph.Alignment>();
						while(!tScaffoldEdges.isEmpty())
						{
							ScaffoldGraph.Alignment cur = tScaffoldEdges.pollFirst();
							Alignment rev = cur.reverse(lastTo);
							rev.from = cur.to;
							toAdd.addLast(rev);
							lastTo = cur.to;
						}
						while(!toAdd.isEmpty())
						{
							res.scaffoldEdges.get(firstContigInScaffold).addLast(toAdd.pollLast());
						}
					}
					
					res.scaffoldEdges.remove(tScaffoldKey);
					
					ArrayDeque<String> tScaffoldContigs = res.scaffoldContigs.get(tScaffoldKey);
					while(!tScaffoldContigs.isEmpty())
					{
						res.scaffoldContigs.get(firstContigInScaffold).addLast(tFirst ? tScaffoldContigs.pollFirst() : tScaffoldContigs.pollLast());
					}
					res.scaffoldContigs.remove(tScaffoldKey);
					if(!tFirst)
					{
						lastToFirst.remove(t);
					}
				}
				else
				{
					if(Settings.VERBOSE)
					{
						System.err.println("  From lone contig to a larger scaffold");
					}
					// s is on its own, so add it to the beginning of the scaffold with t
					if(tFirst)
					{
						res.scaffoldEdges.put(s, res.scaffoldEdges.get(t));
						res.scaffoldEdges.get(s).addFirst(best);
						res.scaffoldEdges.remove(t);
						res.scaffoldContigs.put(s, res.scaffoldContigs.get(t));
						res.scaffoldContigs.get(s).addFirst(s);
						res.scaffoldContigs.remove(t);
						lastToFirst.put(lastContigInScaffold, s);
					}
					else
					{
						// t is at the end of its scaffold, so reverse scaffold and add it to new scaffold
						
						// Deal with edges
						res.scaffoldEdges.put(s, new ArrayDeque<ScaffoldGraph.Alignment>());
						res.scaffoldEdges.get(s).addFirst(best);
						String lastTo = tScaffoldKey;
						ArrayDeque<ScaffoldGraph.Alignment> toAdd = new ArrayDeque<ScaffoldGraph.Alignment>();
						while(!res.scaffoldEdges.get(tScaffoldKey).isEmpty())
						{
							ScaffoldGraph.Alignment cur = res.scaffoldEdges.get(tScaffoldKey).pollFirst();
							Alignment rev = cur.reverse(lastTo);
							rev.from = cur.to;
							toAdd.addLast(rev);
							lastTo = cur.to;
						}
						while(!toAdd.isEmpty())
						{
							res.scaffoldEdges.get(s).addLast(toAdd.pollLast());
						}
						res.scaffoldEdges.remove(tScaffoldKey);
						
						// Deal with list of contigs
						res.scaffoldContigs.put(s, new ArrayDeque<String>());
						res.scaffoldContigs.get(s).addFirst(s);
						while(!res.scaffoldContigs.get(tScaffoldKey).isEmpty())
						{
							res.scaffoldContigs.get(s).addLast(res.scaffoldContigs.get(tScaffoldKey).pollLast());
						}
						res.scaffoldContigs.remove(tScaffoldKey);
						
						// Deal with lastToFirst
						lastToFirst.remove(t);
						lastToFirst.put(tScaffoldKey, s);
					}
					res.usedContigs.add(s);					
				}
			}
			
			res.numMerged++;
			
		}

		return res;
	}
	
	HashMap<String,ArrayList<Alignment>[]> adj;
	ScaffoldGraph()
	{
		adj =  new HashMap<>();
	}
	@SuppressWarnings("unchecked")
	void addEdge(String from, String to, String readName, int fromEnd, int toStart, int readLength, boolean fromPrefix, boolean toPrefix, double weight)
	{
		String[] contigs = new String[] {from, to};
		for(String key : contigs)
		{
			if(!adj.containsKey(key))
			{
				adj.put(key, new ArrayList[2]);
				for(int i = 0; i<adj.get(key).length; i++)
				{
					adj.get(key)[i] = new ArrayList<>();
				}
			}
		}
				
		// Add forward edge
		Alignment al = new Alignment(to, readName, fromEnd, toStart, fromPrefix, toPrefix, 0, weight, readLength);
		adj.get(from)[0].add(al);
		
		// Add reverse edge
		adj.get(to)[1].add(al.reverse(from));
	}
	

static class Alignment implements Comparable<Alignment>
{
	String to;
	String read;
	int myReadEnd;
	int theirReadStart;
	boolean myContigPrefix;
	boolean theirContigPrefix;
	int strand;
	double weight;
	int readLength;
	String from;
	ArrayList<ReadInterval> allReads;
	Alignment(String tt, String rr, int mre, int trs, boolean mcp, boolean tcp, int ss, double ww, int rl)
	{
		to = tt;
		read = rr;
		myReadEnd = mre;
		theirReadStart = trs;
		myContigPrefix = mcp;
		theirContigPrefix = tcp;
		strand = ss;
		weight = ww;
		this.readLength = rl;
		this.allReads = new ArrayList<ReadInterval>();
	}
	
	Alignment reverse(String from)
	{
		Alignment res = new Alignment(from, read, readLength - theirReadStart, readLength - myReadEnd, 
				theirContigPrefix, myContigPrefix, 1 - strand, weight, readLength);
		res.allReads = allReads;
		return res;
	}

	public int compareTo(Alignment o) {
		return Double.compare(o.weight, weight);
	}
}

/*
 * The interval of a read corresponding to an overlap - the contigs being linked are provided to denote the ordering of the endpoints of the interval
 */
static class ReadInterval
{
	String readName;
	int start, end;
	String from;
	String to;
	int strand;
	int readLength;
	ReadInterval(String rr, int ss, int ee, String ff, String tt, int st, int rl)
	{
		readName = rr;
		start = ss;
		end = ee;
		from = ff;
		to = tt;
		strand = st;
		readLength = rl;
	}
	ReadInterval(Alignment a)
	{
		readName = a.read;
		start = a.myReadEnd;
		end = a.theirReadStart;
		to = a.to;
		from = a.from;
		strand = a.strand;
		readLength = a.readLength;
	}
}
}
