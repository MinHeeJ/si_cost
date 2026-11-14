import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { afterEach, expect, it } from "vitest";
import { AnalysisTable } from "./AnalysisTable";
import { PROJECTS_COMMITTED } from "../projects/projectsApi";
const originalWidth=window.innerWidth;
afterEach(()=>{Object.defineProperty(window,"innerWidth",{configurable:true,value:originalWidth});});
it("767px는 서버20건 카드와 더보기를 쓰고 commit 후 첫페이지를 다시 조회한다",async()=>{
  Object.defineProperty(window,"innerWidth",{configurable:true,value:767});
  function Harness(){
    const [page,setPage]=useState(1),[size,setSize]=useState<20|50|100>(50);
    const rows=Array.from({length:page===1?size:1},(_,i)=>({id:(page-1)*size+i+1}));
    return <AnalysisTable rows={rows} columns={[{id:"projectName",label:"프로젝트",render:r=>`프로젝트 ${r.id}`}]} rowKey={r=>r.id} page={page} size={size} total={21} onSizeChange={v=>{setSize(v);setPage(1)}} onPageChange={setPage} />;
  }
  render(<Harness/>);
  await screen.findByText("프로젝트 20");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button",{name:"20개 더보기"}));
  await screen.findByText("프로젝트 21");
  expect(screen.getByText("프로젝트 1")).toBeInTheDocument();
  fireEvent(window,new CustomEvent(PROJECTS_COMMITTED));
  await waitFor(()=>expect(screen.queryByText("프로젝트 21")).not.toBeInTheDocument());
  fireEvent(window,new Event("resize"));
});
it("768px는 기존 DataTable의 열 머리 정렬과 페이지 크기를 유지한다",()=>{
  Object.defineProperty(window,"innerWidth",{configurable:true,value:768});
  render(<AnalysisTable rows={[{id:1}]} columns={[{id:"projectName",label:"프로젝트",render:()=>"기존표",sortable:true}]} rowKey={r=>r.id} size={50}/>);
  expect(screen.getByRole("table")).toBeInTheDocument();
  expect(screen.getByText("기존표")).toBeInTheDocument();
  expect(screen.queryByRole("button",{name:"20개 더보기"})).not.toBeInTheDocument();
});
