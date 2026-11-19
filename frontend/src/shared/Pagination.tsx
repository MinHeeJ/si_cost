export function Pagination({page,size,total,onPageChange,onSizeChange,loading=false,testId="table",label="페이지",unit="건",hideSingle=false}:{page:number;size:number;total:number;onPageChange?(page:number):void;onSizeChange?(size:20|50|100):void;loading?:boolean;testId?:string;label?:string;unit?:string;hideSingle?:boolean}) {
 if(loading || hideSingle && total<=size)return null;
 const pages=Math.max(1,Math.ceil(total/size)),start=Math.max(1,Math.min(page-2,pages-4));
 return <nav aria-label={label} className="sicost-pagination"><span>{total?(page-1)*size+1:0}-{Math.min(page*size,total)} / {total}{unit}</span><div>
 <button type="button" aria-label="처음 페이지" data-testid={`${testId}-first-page`} disabled={page<=1} onClick={()=>onPageChange?.(1)}>«</button>
 <button type="button" aria-label="이전 페이지" data-testid={`${testId}-previous-page`} disabled={page<=1} onClick={()=>onPageChange?.(page-1)}>‹</button>
 {Array.from({length:Math.min(5,pages)},(_,i)=>start+i).map(n=><button type="button" key={n} aria-label={`${n} 페이지`} aria-current={n===page?"page":undefined} onClick={()=>onPageChange?.(n)}>{n}</button>)}
 <button type="button" aria-label="다음 페이지" data-testid={`${testId}-next-page`} disabled={page>=pages} onClick={()=>onPageChange?.(page+1)}>›</button>
 <button type="button" aria-label="마지막 페이지" data-testid={`${testId}-last-page`} disabled={page>=pages} onClick={()=>onPageChange?.(pages)}>»</button>
 </div>{onSizeChange&&<select aria-label="한 화면 건수" data-testid={`${testId}-page-size`} value={size} onChange={e=>onSizeChange(Number(e.target.value) as 20|50|100)}>{[20,50,100].map(n=><option key={n} value={n}>{n}건씩</option>)}</select>}</nav>;
}
