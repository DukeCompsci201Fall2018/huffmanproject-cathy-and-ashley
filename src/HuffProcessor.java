import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	
	public void compress(BitInputStream in, BitOutputStream out){
		//int val = in.readBits(BITS_PER_INT);
		//if (val == -1) break;
		//out.writeBits(BITS_PER_INT, val);
		
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}
	
	/**
	 * Helper method for compress that outputs the length of the code, translates the binary encoding
	 * @param in Buffered bit stream of the file to be compressed.
	 * @param out Buffered bit stream writing to the output file.
	 * @param codings are the encodings for the 8-bit chunks stored in an array
	 */
	
	//this is wrong
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while (true) {
			int value = in.readBits(BITS_PER_WORD);
			if(value == -1) {
				String c = codings[PSEUDO_EOF];
				out.writeBits(c.length(), Integer.parseInt(c,2));
				break;

			}
			else {
				String code = codings[value];
				if(code == null || out == null) {
					break;
				}
				out.writeBits(code.length(), Integer.parseInt(code,2));
			}
		}
	}
	
	/**
	 * Helper method for compress that writes the bits from the tree 
	 * @param root the tree that contains the
	 * @param out is the Buffered bit stream writing to the output file
	 */
	//is line 98 right?
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if(root == null) {
			return;
		}
		
		if(root.myLeft != null || root.myRight != null) {
			out.writeBits(1,0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		
		else {
			out.writeBits(1,1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
	}
	
	/**
	 * Helper method for compress that creates a tree from the codings
	 * @param root is the tree that make up the elements of the encoding
	 * @return an array of strings such that the value at the index at the array is the encoding
	 * of the value
	 */
	
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root,"", encodings);
		return encodings;
	}
	/**
	 * Helper method for makeCodingsFromTree that actually creates the 0,1 path with recursive
	 * calls
	 * @param root the tree from makingCodingsFromTree
	 * @param path is the binary encoding path of the leaves
	 * @param encodings is the array to which the codings from the tree transversal
	 * is created
	 */

	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if(root == null) return;
		if(root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			return;
		}
		codingHelper(root.myLeft, path+"0", encodings);
		codingHelper(root.myRight, path+"1", encodings);
	}
	
	/**
	 * Helper method for compress that uses a priority queue to make the tree
	 * that will be later used to make the encodings
	 * @param counts is the integer array that contains the frequencies
	 * @return the tree with the counts stored 
	 */

	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for(int i = 0; i < counts.length; i++) {
			if(counts[i] > 0) {
				pq.add(new HuffNode(i, counts[i], null, null));
			}
		}
		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			//should the value to 0
			HuffNode t = new HuffNode(left.myValue + right.myValue, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		
		HuffNode root = pq.remove();
		return root;
	}

	/**
	 * determine the frequency of every 8-bit character/chunk in the file 
	 * being compressed 
	 * @param in is the Buffered bit stream of the file to be compressed
	 * @return the array of frequencies of the 8-bit chunks in the file
	 */
	
	private int[] readForCounts(BitInputStream in) {
		int[] arrayint =  new int[ALPH_SIZE +1];
		while(true) {
		int value = in.readBits(BITS_PER_WORD);
		if(value == -1) {
			break;
			}
		arrayint[value]++;
		}
		arrayint[PSEUDO_EOF] = 1;
		return arrayint;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	
	public void decompress(BitInputStream in, BitOutputStream out){

		//while (true){
			int val = in.readBits(BITS_PER_INT); //reads the 32-bit magic number
			if(val != HUFF_TREE) {
				throw new HuffException("illegal header starts with "+val);
			}
			
			if (val == -1) {
				throw new HuffException("illegal header starts with "+ val);
			}
			
			HuffNode root = readTreeHeader(in);
			readCompressedBits(root, in, out);
			//out.writeBits(BITS_PER_WORD, val);
			out.close();
		//}
	}

	/**
	 * read the bits from the compressed file and use them to traverse root-to-leaf paths
	 * writes leaf values to the output file
	 * @param root the tree 
	 * @param in Buffered bit stream of the file to be decompressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
				int bits = in.readBits(1);
				if(bits == -1) {
					throw new HuffException("bad input, no PSEUDO_EOF");
				}
				else {
					if (bits == 0) current = current.myLeft;
					else {
						current = current.myRight;
					}
					if(current.myLeft == null && current.myRight == null) {
						if(current.myValue == PSEUDO_EOF) break;
						else {
							int currentvalue = current.myValue;
							out.writeBits(BITS_PER_WORD, currentvalue);
							current = root;
						}
					}
				}
			}	
		}
	
	/**
	 * read the tree used to decompress and return internal and leaf nodes
	 * @param in Buffered bit stream of the file to be decompressed.
	 * @return tree from the in stream
	 */
	
	private HuffNode readTreeHeader(BitInputStream in) {
		HuffNode root = new HuffNode(0, 0);
			int singlebit = in.readBits(1);
			if (singlebit == -1) {
				throw new HuffException("there is no tree " + singlebit);
			}
			if(singlebit == 0) {
				root.myLeft = readTreeHeader(in);
				root.myRight = readTreeHeader(in);
				return new HuffNode(0,0,root.myLeft, root.myRight);
			}
			else{
				int value = in.readBits(9);
				return new HuffNode(value,0,null,null);
			}
	
	}
}